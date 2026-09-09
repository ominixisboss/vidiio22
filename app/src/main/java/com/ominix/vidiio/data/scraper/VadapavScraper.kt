package com.ominix.vidiio.data.scraper

import com.ominix.vidiio.data.model.Category
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.MovieType
import com.ominix.vidiio.data.model.Episode
import com.ominix.vidiio.data.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import android.util.Log
import kotlinx.coroutines.CancellationException

class VadapavScraper(private val client: OkHttpClient) : Scraper {
    override val name: String = "Vadapav"
    override val sourceId: String = "vadapav"
    override val baseUrl: String = "https://stremio.vadapav.mov"

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    override suspend fun getHomeCategories(): List<Category> = emptyList()
    override suspend fun search(query: String): List<Movie> = emptyList()
    override suspend fun getMovieDetails(movie: Movie): Movie = movie

    override suspend fun getStreamSources(movie: Movie, episode: Episode?): List<StreamSource> = withContext(Dispatchers.IO) {
        val sources = mutableListOf<StreamSource>()
        val isTv = movie.type == MovieType.TV_SHOW

        // vadapav.mov is a Stremio addon: it keys on an IMDb id, or a "tmdb:<id>" id.
        val targetIds = buildList {
            movie.imdbId?.takeIf { it.startsWith("tt") }?.let { add(it) }
            if (movie.id.startsWith("tt")) add(movie.id) else add("tmdb:${movie.id}")
        }.distinct()

        for (id in targetIds) {
            val endpoint = if (isTv) {
                val s = episode?.seasonNumber ?: 1
                val e = episode?.episodeNumber ?: 1
                "$baseUrl/stream/series/$id:$s:$e.json"
            } else {
                "$baseUrl/stream/movie/$id.json"
            }

            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val streams = JSONObject(body).optJSONArray("streams")
                        if (streams != null) {
                            for (i in 0 until streams.length()) {
                                val stream = streams.getJSONObject(i)
                                val url = stream.optString("url")
                                if (url.isEmpty() || !url.startsWith("http")) continue

                                val streamTitle = stream.optString("title", "vadapav.mov")
                                sources.add(StreamSource(
                                    serverName = stream.optString("name", "vadapav.mov"),
                                    url = url,
                                    sourceName = name,
                                    quality = if (streamTitle.contains("4K", ignoreCase = true)) "4K" else "HD",
                                    isM3u8 = url.contains(".m3u8"),
                                    headers = mapOf("User-Agent" to userAgent)
                                ))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("VadapavScraper", "Error ($id): ${e.message}")
            }
            if (sources.isNotEmpty()) break
        }

        sources
    }
}
