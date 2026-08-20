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
        liveLinkCache.clear()
        val mac = stalkerMac()
        ensureSession(mac)
        syncAllBlocking(Credentials(mac, "stalker")).getOrThrow()
        auth.markActivated()
        XtreamAccount(true, "Active", null)
    }

    fun updateMacBlocking(mac: String): Result<Unit> = runCatching {
        auth.saveMac(mac, activate = false)
        token = null
        liveLinkCache.clear()
        val normalized = stalkerMac()
        ensureSession(normalized)
        syncAllBlocking(Credentials(normalized, "stalker")).getOrThrow()
        auth.markActivated()
    }

    fun syncAllBlocking(credentials: Credentials? = auth.get()): Result<Unit> = runCatching {
        val mac = credentials?.username ?: stalkerMac()
        token = null
        liveLinkCache.clear()
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

    fun playbackUrl(item: StreamItem): String = playbackUrls(item).first()

    fun playbackUrls(item: StreamItem): List<String> {
        val cmd = item.directSource.trim()
        require(cmd.isNotBlank()) { "Komanda e kanalit mungon" }
        if (cmd.startsWith("http://", true) || cmd.startsWith("https://", true)) return listOf(cmd)

        if (item.type == ContentType.LIVE) {
            val now = System.currentTimeMillis()
            liveLinkCache[item.id]?.takeIf { now - it.createdAt < liveLinkTtlMs }?.let {
                return listOf(it.url)
            }
        }

        val mac = stalkerMac()
        var t = ensureSession(mac)
        val resolved = runCatching { api.createLink(mac, t, item.type, cmd) }.getOrElse {
            token = null
            t = ensureSession(mac)
            api.createLink(mac, t, item.type, cmd)
        }

        if (item.type == ContentType.LIVE) {
            liveLinkCache[item.id] = CachedLink(resolved, System.currentTimeMillis())
        }
        return listOf(resolved)
    }

    fun logout() {
        liveLinkCache.clear()
        auth.clear()
    }
}
