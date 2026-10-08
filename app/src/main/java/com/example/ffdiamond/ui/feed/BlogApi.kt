package com.example.ffdiamond.ui.feed

import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class BlogPost(
    val id: String,
    val title: String,
    val summary: String,
    val imageUrl: String,
    val url: String,
    val category: String,
    val categoryColor: String,
    val publishedAt: String,
    val readingMinutes: Int
)

data class BlogPage(
    val items: List<BlogPost>,
    val nextUrl: String?,
    val hasMore: Boolean,
    val page: Int
)

/**
 * Hindi Gyan list endpoint. Parsing is kept here so the feed UI never touches raw JSON.
 */
object BlogApi {

    const val ENDPOINT = "https://hindigyan.in/api.php"

    fun fetch(
        page: Int,
        locale: Locale = Locale.getDefault(),
        endpoint: String = ENDPOINT
    ): BlogPage {
        val api = endpoint.ifBlank { ENDPOINT }
        return download(urlFor(api, page, locale.language))
    }

    fun fetchNext(nextUrl: String): BlogPage = download(nextUrl)

    fun parse(json: String): BlogPage {
        val root = JSONObject(json)
        val array = root.optJSONArray("items")
        val items = buildList {
            if (array != null) {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val title = item.optString("title")
                    if (id.isBlank() || title.isBlank()) continue
                    add(
                        BlogPost(
                            id = id,
                            title = title,
                            summary = item.optString("summary"),
                            imageUrl = item.optString("image"),
                            url = item.optString("url"),
                            category = item.optString("category"),
                            categoryColor = item.optString("category_color"),
                            publishedAt = item.optString("published_at"),
                            readingMinutes = item.optInt("reading_minutes", 0)
                        )
                    )
                }
            }
        }
        return BlogPage(
            items = items,
            nextUrl = root.optString("next").takeIf { it.isNotBlank() && it != "null" },
            hasMore = root.optBoolean("has_more"),
            page = root.optInt("page", 1)
        )
    }

    private fun urlFor(endpoint: String, page: Int, language: String?): String {
        val params = mutableListOf("limit=20", "page=$page")
        val lang = language?.trim()?.lowercase(Locale.US)?.takeIf { it.length in 2..5 }
        if (lang != null && lang != "ja") params += "lang=$lang"
        val base = endpoint.substringBefore("?")
        return "$base?${params.joinToString("&")}"
    }

    private fun download(url: String): BlogPage {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
        }
        return try {
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                error("HTTP ${connection.responseCode}")
            }
            val body = stream.bufferedReader().use(BufferedReader::readText)
            parse(body)
        } finally {
            connection.disconnect()
        }
    }
}
