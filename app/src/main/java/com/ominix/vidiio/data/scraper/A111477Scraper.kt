package com.ominix.vidiio.data.scraper

import com.ominix.vidiio.data.model.Category
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.MovieType
import com.ominix.vidiio.data.model.Episode
import com.ominix.vidiio.data.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CancellationException

class A111477Scraper(private val client: OkHttpClient) : Scraper {
    override val name: String = "A111477"
    override val sourceId: String = "a111477"
    override val baseUrl: String = "https://st.111477.xyz"

    private val serviceOrigin = "https://st.111477.xyz"
    private val defaultStreamHost = "https://a.111477.xyz/"
    private val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val defaultHeaders = mapOf(
        "User-Agent" to ua,
        "Accept" to "application/json, text/plain, */*"
    )

    private fun createRequest(url: String): Request {
        val builder = Request.Builder().url(url)
        defaultHeaders.forEach { (k, v) -> builder.header(k, v) }
        return builder.build()
    }

    override suspend fun getHomeCategories(): List<Category> = emptyList()

    override suspend fun search(query: String): List<Movie> = emptyList()

    override suspend fun getMovieDetails(movie: Movie): Movie = movie

    override suspend fun getStreamSources(movie: Movie, episode: Episode?): List<StreamSource> = withContext(Dispatchers.IO) {
        val isTv = movie.type == MovieType.TV_SHOW
        val targetIds = mutableListOf<String>()

        // 1. If we have an IMDB ID, use it.
        // In current project, movie.id might be TMDB or IMDB depending on source.
        // If it starts with 'tt', it's IMDB.
        if (movie.id.startsWith("tt")) {
            targetIds.add(movie.id)
        } else {
            // Fallback: try using it as tmdb ID
            targetIds.add("tmdb:${movie.id}")
        }

        val addonBase = generateManifestBaseUrl(limit = 3)
        val seenUrls = mutableSetOf<String>()
        val sources = mutableListOf<StreamSource>()

        for (id in targetIds) {
            val endpoint = if (isTv) {
                "$addonBase/stream/series/$id:${episode?.seasonNumber ?: 1}:${episode?.episodeNumber ?: 1}.json"
            } else {
                "$addonBase/stream/movie/$id.json"
            }

            try {
                val res = withTimeoutOrNull(12000.milliseconds) {
                    client.newCall(createRequest(endpoint)).execute()
                } ?: continue

                if (res.isSuccessful) {
                    val body = res.body?.string() ?: continue
                    val json = JSONObject(body)
                    val streams = json.optJSONArray("streams") ?: continue

                    for (i in 0 until streams.length()) {
                        val item = streams.optJSONObject(i) ?: continue
                        val url = item.optString("url")
                        if (url.isEmpty() || !url.startsWith("http") || !seenUrls.add(url)) continue

                        val rawTitle = item.optString("title", "")
                        val rawName = item.optString("name", "111477")

                        sources.add(StreamSource(
                            serverName = "[$rawName] ${if (rawTitle.isNotEmpty()) rawTitle else "Direct Stream"}",
                            url = url,
                            sourceName = name,
                            quality = parseQuality(rawTitle),
                            isM3u8 = url.contains(".m3u8")
                        ))
                    }
                    if (streams.length() > 0) break // Found streams, skip fallback IDs
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("A111477Scraper", "Error fetching from $endpoint", e)
            }
        }
        sources
    }

    private fun generateManifestBaseUrl(host: String = defaultStreamHost, sort: String = "file-desc", limit: Int = 3): String {
        var config = host.trim()
        if (!config.endsWith("/")) config += "/"
        if (sort.isNotEmpty() && sort != "none") config += "::sort=$sort"
        if (limit > 0 && limit != 5) config += "::limit=$limit"

        val bytes = config.toByteArray()
        val b64 = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return "$serviceOrigin/config/$b64"
    }

    private fun parseQuality(title: String): String {
        val lower = title.lowercase()
        return when {
            lower.contains("4k") || lower.contains("2160p") -> "4K"
            lower.contains("1080p") -> "1080p"
            lower.contains("720p") -> "720p"
            lower.contains("480p") -> "480p"
            else -> "HD"
        }
    }
}
