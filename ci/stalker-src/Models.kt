package al.speedline.iptv.data

import org.json.JSONArray
import org.json.JSONObject

data class Credentials(val username: String, val password: String)

data class XtreamAccount(
    val authenticated: Boolean,
    val status: String,
    val expiration: Long?
)

enum class ContentType { LIVE, MOVIE, SERIES }

data class Category(val id: String, val name: String)

data class StreamItem(
    val id: Int,
    val name: String,
    val categoryId: String,
    val type: ContentType,
    val icon: String = "",
    val containerExtension: String = "",
    val plot: String = "",
    val directSource: String = "",
    val channelNumber: Int? = null
)

object XtreamParsers {
    fun account(json: String): XtreamAccount {
        val root = JSONObject(json)
        val info = root.optJSONObject("user_info") ?: JSONObject()
        val auth = info.optInt("auth", 0) == 1
        val status = info.optString("status", "")
        val exp = info.optString("exp_date", "").toLongOrNull()
        return XtreamAccount(auth, status, exp)
    }

    fun categories(json: String): List<Category> {
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("category_id", "")
                val name = o.optString("category_name", "")
                if (id.isNotBlank()) add(Category(id, name.ifBlank { "Pa kategori" }))
            }
        }
    }

    fun episodes(json: String): List<StreamItem> {
        val root = JSONObject(json)
        val seasons = root.optJSONObject("episodes") ?: return emptyList()
        val result = mutableListOf<StreamItem>()
        val keys = seasons.keys()
        while (keys.hasNext()) {
            val seasonKey = keys.next()
            val arr = seasons.optJSONArray(seasonKey) ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id", "").toIntOrNull() ?: o.optInt("id", 0)
                if (id <= 0) continue
                val epNum = o.optInt("episode_num", i + 1)
                val title = o.optString("title", "Episode $epNum")
                val season = seasonKey.toIntOrNull() ?: 0
                val prefix = if (season > 0) "S%02d E%02d".format(season, epNum) else "E%02d".format(epNum)
                result += StreamItem(
                    id = id,
                    name = "$prefix • $title",
                    categoryId = seasonKey,
                    type = ContentType.SERIES,
                    icon = o.optJSONObject("info")?.optString("movie_image", "") ?: "",
                    containerExtension = o.optString("container_extension", "mp4"),
                    plot = o.optJSONObject("info")?.optString("plot", "") ?: "",
                    directSource = o.optString("direct_source", "")
                )
            }
        }
        return result.sortedWith(compareBy<StreamItem> { it.categoryId.toIntOrNull() ?: 0 }.thenBy { it.name })
    }

    fun streams(json: String, type: ContentType): List<StreamItem> {
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = when (type) {
                    ContentType.SERIES -> o.optInt("series_id", 0)
                    else -> o.optInt("stream_id", 0)
                }
                if (id <= 0) continue
                val name = o.optString("name", "Stream $id")
                val icon = when (type) {
                    ContentType.SERIES -> o.optString("cover", "")
                    else -> o.optString("stream_icon", "")
                }
                add(
                    StreamItem(
                        id = id,
                        name = name,
                        categoryId = o.optString("category_id", ""),
                        type = type,
                        icon = icon,
                        containerExtension = o.optString("container_extension", ""),
                        plot = o.optString("plot", ""),
                        directSource = o.optString("direct_source", ""),
                        channelNumber = if (type == ContentType.LIVE) o.optInt("num", 0).takeIf { it > 0 } else null
                    )
                )
            }
        }
    }
}
