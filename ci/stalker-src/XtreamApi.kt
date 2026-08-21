package al.speedline.iptv.data

import al.speedline.iptv.AppConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

class XtreamApi {
    private fun enc(v: String) = URLEncoder.encode(v, Charsets.UTF_8.name())

    private val endpointCandidates: List<String>
        get() {
            val root = AppConfig.STALKER_PORTAL_URL.trimEnd('/').removeSuffix("/c")
            return listOf(
                "$root/portal.php",
                "$root/server/load.php",
                "${root.trimEnd('/')}/stalker_portal/server/load.php"
            ).distinct()
        }

    private fun request(mac: String, token: String?, type: String, action: String, extra: Map<String, String> = emptyMap()): String {
        val params = linkedMapOf("type" to type, "action" to action)
        params.putAll(extra)
        params["JsHttpRequest"] = "1-xml"
        var last: Throwable? = null
        for (endpoint in endpointCandidates) {
            try {
                val query = params.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
                val url = URI.create("$endpoint?$query").toURL()
                val c = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 30_000
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", AppConfig.APP_USER_AGENT)
                    setRequestProperty("X-User-Agent", AppConfig.STB_USER_AGENT)
                    setRequestProperty("Cookie", "mac=${enc(mac)}; stb_lang=en; timezone=Europe/Tirane;")
                    setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01")
                    setRequestProperty("Referer", AppConfig.STALKER_PORTAL_URL.trimEnd('/') + "/")
                    if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
                    useCaches = false
                }
                try {
                    val code = c.responseCode
                    val stream = if (code in 200..299) c.inputStream else c.errorStream
                    val body = BufferedReader(InputStreamReader(stream)).use { it.readText() }
                    if (code !in 200..299) error("HTTP $code")
                    if (body.isBlank() || (!body.trimStart().startsWith("{") && !body.trimStart().startsWith("["))) error("Përgjigje jo-JSON nga portal")
                    return body
                } finally { c.disconnect() }
            } catch (t: Throwable) { last = t }
        }
        throw last ?: IllegalStateException("Stalker portal unavailable")
    }

    fun handshake(mac: String): String {
        val body = request(mac, null, "stb", "handshake", mapOf("token" to ""))
        return JSONObject(body).optJSONObject("js")?.optString("token", "").orEmpty().ifBlank { error("Token mungon") }
    }

    fun profile(mac: String, token: String) {
        runCatching {
            request(mac, token, "stb", "get_profile", mapOf(
                "hd" to "1", "stb_type" to "MAG254", "image_version" to "218",
                "auth_second_step" to "1", "not_valid_token" to "0"
            ))
        }
    }

    fun liveCategories(mac: String, token: String): String = categoriesToXtream(request(mac, token, "itv", "get_genres"))
    fun liveStreams(mac: String, token: String): String = liveToXtream(request(mac, token, "itv", "get_all_channels"))
    fun vodCategories(mac: String, token: String): String = categoriesToXtream(request(mac, token, "vod", "get_categories"))

    fun vodStreams(mac: String, token: String): String {
        val out = JSONArray()
        var page = 1
        var syntheticId = 1
        while (page <= 500) {
            val body = request(mac, token, "vod", "get_ordered_list", mapOf(
                "genre" to "0", "force_ch_link_check" to "", "fav" to "0",
                "sortby" to "added", "hd" to "0", "p" to page.toString()
            ))
            val js = JSONObject(body).opt("js")
            val data = when (js) {
                is JSONObject -> js.optJSONArray("data") ?: JSONArray()
                is JSONArray -> js
                else -> JSONArray()
            }
            if (data.length() == 0) break
            for (i in 0 until data.length()) {
                val v = data.optJSONObject(i) ?: continue
                val id = v.optString("id", "").toIntOrNull() ?: syntheticId++
                out.put(JSONObject().apply {
                    put("stream_id", id)
                    put("name", v.optString("name", v.optString("title", "Film $id")))
                    put("category_id", v.optString("category_id", v.optString("genre_id", "0")))
                    put("stream_icon", v.optString("screenshot_uri", v.optString("logo", "")))
                    put("container_extension", "")
                    put("plot", v.optString("description", v.optString("descr", "")))
                    put("direct_source", v.optString("cmd", v.optString("file", "")))
                })
            }
            val total = (js as? JSONObject)?.optInt("total_items", -1) ?: -1
            val maxPage = (js as? JSONObject)?.optInt("max_page_items", -1) ?: -1
            if (total > 0 && maxPage > 0 && page * maxPage >= total) break
            page++
        }
        return out.toString()
    }

    fun createLink(mac: String, token: String, contentType: ContentType, cmd: String): String {
        val type = if (contentType == ContentType.LIVE) "itv" else "vod"
        val body = request(mac, token, type, "create_link", mapOf(
            "cmd" to cmd, "series" to "", "forced_storage" to "undefined",
            "disable_ad" to "0", "download" to "0"
        ))
        var resolved = JSONObject(body).optJSONObject("js")?.optString("cmd", "").orEmpty().trim()
        if (resolved.startsWith("ffmpeg ", true) || resolved.startsWith("ffrt ", true)) resolved = resolved.substringAfter(' ').trim()
        return resolved.ifBlank { error("Stream URL mungon") }
    }

    private fun categoriesToXtream(body: String): String {
        val js = JSONObject(body).opt("js")
        val arr = when (js) {
            is JSONArray -> js
            is JSONObject -> js.optJSONArray("data") ?: JSONArray()
            else -> JSONArray()
        }
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id", o.optString("category_id", ""))
            if (id.isBlank()) continue
            out.put(JSONObject().apply {
                put("category_id", id)
                put("category_name", o.optString("title", o.optString("name", "Pa kategori")))
            })
        }
        return out.toString()
    }

    private fun liveToXtream(body: String): String {
        val js = JSONObject(body).opt("js")
        val arr = when (js) {
            is JSONArray -> js
            is JSONObject -> js.optJSONArray("data") ?: JSONArray()
            else -> JSONArray()
        }
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id", "").toIntOrNull() ?: o.optInt("id", 0)
            if (id <= 0) continue
            out.put(JSONObject().apply {
                put("stream_id", id)
                put("num", o.optInt("number", o.optInt("id", i + 1)))
                put("name", o.optString("name", "Kanal $id"))
                put("category_id", o.optString("tv_genre_id", o.optString("genre_id", "0")))
                put("stream_icon", o.optString("logo", ""))
                put("direct_source", o.optString("cmd", ""))
            })
        }
        return out.toString()
    }
}
