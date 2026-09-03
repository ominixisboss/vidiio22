package com.example.vidiio.data.scraper

import com.example.vidiio.data.model.Category
import com.example.vidiio.data.model.Movie
import com.example.vidiio.data.model.MovieType
import com.example.vidiio.data.model.Episode
import com.example.vidiio.data.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import android.util.Log

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
        
        val id = movie.id // Assuming movie.id is the ID needed (TMDB or IMDB)
        
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
                    val json = JSONObject(body)
                    val streams = json.optJSONArray("streams")
                    if (streams != null) {
                        for (i in 0 until streams.length()) {
                            val stream = streams.getJSONObject(i)
                            val url = stream.optString("url")
                            if (url.isEmpty() || !url.startsWith("http")) continue
                            
                            val streamTitle = stream.optString("title", "vadapav.mov")
                            val streamName = stream.optString("name", "vadapav.mov")

                            sources.add(StreamSource(
                                serverName = streamName,
                                url = url,
                                sourceName = name,
                                quality = if (streamTitle.contains("4K", ignoreCase = true)) "4K" else "HD",
                                isM3u8 = url.contains(".m3u8")
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VadapavScraper", "Error: ${e.message}")
        }
        
        sources
    }
}
