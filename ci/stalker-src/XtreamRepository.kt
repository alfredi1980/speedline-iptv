package al.speedline.iptv.data

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

class XtreamRepository(context: Context) {
    private val app = context.applicationContext
    private val auth = CredentialsStore(app)
    private val cache = CacheStore(app)
    private val api = XtreamApi()
    @Volatile private var token: String? = null

    private data class CachedLink(val url: String, val createdAt: Long)
    private val liveLinkCache = ConcurrentHashMap<Int, CachedLink>()
    private val liveLinkTtlMs = 90_000L
    private val portalLinkLock = Any()

    fun credentials(): Credentials? = auth.get()
    fun stalkerMac(): String = auth.currentMac()
    fun lastSuccessfulSync(): Long = cache.lastSuccessfulSync()

    private fun ensureSession(mac: String = stalkerMac()): String {
        token?.takeIf { it.isNotBlank() }?.let { return it }
        return api.handshake(mac).also { t -> token = t; api.profile(mac, t) }
    }

    fun loginBlocking(username: String, password: String = "stalker"): Result<XtreamAccount> = runCatching {
        auth.saveMac(username, activate = false)
        token = null
        clearPlaybackCache()
        val mac = stalkerMac()
        ensureSession(mac)
        syncAllBlocking(Credentials(mac, "stalker")).getOrThrow()
        auth.markActivated()
        XtreamAccount(true, "Active", null)
    }

    fun updateMacBlocking(mac: String): Result<Unit> = runCatching {
        auth.saveMac(mac, activate = false)
        token = null
        clearPlaybackCache()
        val normalized = stalkerMac()
        ensureSession(normalized)
        syncAllBlocking(Credentials(normalized, "stalker")).getOrThrow()
        auth.markActivated()
    }

    fun syncAllBlocking(credentials: Credentials? = auth.get()): Result<Unit> = runCatching {
        val mac = credentials?.username ?: stalkerMac()
        // A catalogue refresh may run while the user is watching TV. Keep the
        // active portal session and resolved live links so refresh cannot stall
        // or invalidate an in-flight create_link request.
        val t = ensureSession(mac)
        val payloads = linkedMapOf(
            "live_categories.json" to api.liveCategories(mac, t),
            "live_streams.json" to api.liveStreams(mac, t),
            "vod_categories.json" to api.vodCategories(mac, t),
            "vod_streams.json" to api.vodStreams(mac, t),
            "series_categories.json" to "[]",
            "series.json" to "[]"
        )
        payloads.forEach { (name, json) -> cache.writeAtomic(name, json) }
        cache.markSuccessfulSync()
    }

    fun syncAsync(onDone: (Result<Unit>) -> Unit = {}) {
        Thread {
            val result = syncAllBlocking()
            android.os.Handler(app.mainLooper).post { onDone(result) }
        }.start()
    }

    fun categories(type: ContentType): List<Category> {
        val name = when (type) {
            ContentType.LIVE -> "live_categories.json"
            ContentType.MOVIE -> "vod_categories.json"
            ContentType.SERIES -> "series_categories.json"
        }
        return cache.read(name)?.let { runCatching { XtreamParsers.categories(it) }.getOrDefault(emptyList()) } ?: emptyList()
    }

    fun streams(type: ContentType, categoryId: String? = null): List<StreamItem> {
        val name = when (type) {
            ContentType.LIVE -> "live_streams.json"
            ContentType.MOVIE -> "vod_streams.json"
            ContentType.SERIES -> "series.json"
        }
        val all = cache.read(name)?.let { runCatching { XtreamParsers.streams(it, type) }.getOrDefault(emptyList()) } ?: emptyList()
        return if (categoryId.isNullOrBlank()) all else all.filter { it.categoryId == categoryId }
    }

    fun seriesEpisodesBlocking(seriesId: Int): Result<List<StreamItem>> = Result.success(emptyList())

    private fun freshCachedLiveUrl(itemId: Int): String? {
        val now = System.currentTimeMillis()
        val cached = liveLinkCache[itemId] ?: return null
        if (now - cached.createdAt >= liveLinkTtlMs) {
            liveLinkCache.remove(itemId, cached)
            return null
        }
        return cached.url
    }

    private fun resolveFromPortal(item: StreamItem): String {
        val cmd = item.directSource.trim()
        require(cmd.isNotBlank()) { "Komanda e kanalit mungon" }
        if (cmd.startsWith("http://", true) || cmd.startsWith("https://", true)) return cmd

        return synchronized(portalLinkLock) {
            val mac = stalkerMac()
            val currentToken = ensureSession(mac)
            runCatching {
                api.createLink(mac, currentToken, item.type, cmd)
            }.getOrElse {
                token = null
                val freshToken = ensureSession(mac)
                api.createLink(mac, freshToken, item.type, cmd)
            }
        }
    }

    private fun resolveLiveAndCache(item: StreamItem): String {
        freshCachedLiveUrl(item.id)?.let { return it }
        val resolved = resolveFromPortal(item)
        liveLinkCache[item.id] = CachedLink(resolved, System.currentTimeMillis())
        return resolved
    }

    fun playbackUrl(item: StreamItem): String = playbackUrls(item).first()

    fun playbackUrls(item: StreamItem): List<String> {
        val cmd = item.directSource.trim()
        require(cmd.isNotBlank()) { "Komanda e kanalit mungon" }
        if (cmd.startsWith("http://", true) || cmd.startsWith("https://", true)) return listOf(cmd)

        if (item.type == ContentType.LIVE) {
            freshCachedLiveUrl(item.id)?.let { return listOf(it) }
            return listOf(resolveLiveAndCache(item))
        }

        return listOf(resolveFromPortal(item))
    }

    private fun clearPlaybackCache() {
        liveLinkCache.clear()
    }

    fun logout() {
        clearPlaybackCache()
        auth.clear()
    }
}
