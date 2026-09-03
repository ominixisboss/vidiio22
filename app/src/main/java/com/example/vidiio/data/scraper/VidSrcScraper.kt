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

class VidSrcScraper(private val client: OkHttpClient) : Scraper {
    override val name: String = "VidSrc"
    override val sourceId: String = "vidsrc"
    override val baseUrl: String = "https://data.vidsrcme.ru"

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private val referer = "https://cloudorchestranova.com/"

    override suspend fun getHomeCategories(): List<Category> = emptyList()
    override suspend fun search(query: String): List<Movie> = emptyList()
    override suspend fun getMovieDetails(movie: Movie): Movie = movie

    override suspend fun getStreamSources(movie: Movie, episode: Episode?): List<StreamSource> = withContext(Dispatchers.IO) {
        val sources = mutableListOf<StreamSource>()
        val tmdbId = movie.id // Assuming movie.id is TMDB ID when source is tmdb
        
        try {
            val isTv = movie.type == MovieType.TV_SHOW
            val type = if (isTv) "tv" else "movie"
            
            var url = "$baseUrl/api.php?type=$type&tmdb=$tmdbId&stream_urls="
            if (isTv && episode != null) {
                url += "&season=${episode.seasonNumber}&episode=${episode.episodeNumber}"
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Referer", referer)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    if (json.optString("status_code") == "200") {
                        val data = json.optJSONObject("data")
                        val streamUrls = data?.optJSONArray("stream_urls")
                        if (streamUrls != null) {
                            for (i in 0 until streamUrls.length()) {
                                val streamUrl = streamUrls.getString(i).trim()
                                if (streamUrl.isEmpty() || !streamUrl.startsWith("http")) continue
                                
                                sources.add(StreamSource(
                                    serverName = if (streamUrls.length() > 1) "VidSrc ${i + 1}" else "VidSrc",
                                    url = streamUrl,
                                    sourceName = name,
                                    quality = if (streamUrl.contains(".m3u8")) "HD" else "SD",
                                    isM3u8 = streamUrl.contains(".m3u8")
                                ))
                            }
                        }
                    }
                }
            }

            // Fallback
            if (sources.isEmpty()) {
                val embedBase = "https://vidsrc.me"
                val embedUrl = if (isTv) {
                    "$embedBase/embed/tv/$tmdbId/${episode?.seasonNumber ?: 1}/${episode?.episodeNumber ?: 1}"
                } else {
                    "$embedBase/embed/movie/$tmdbId"
                }

                val embedRequest = Request.Builder()
                    .url(embedUrl)
                    .header("User-Agent", userAgent)
                    .header("Referer", "https://vidsrc.me/")
                    .build()

                client.newCall(embedRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val html = response.body?.string() ?: ""
                        val m3u8Regex = Regex("https?://[^\"'\\s]+\\.m3u8[^\"'\\s]*")
                        val matches = m3u8Regex.findAll(html)
                        for (match in matches) {
                            val matchUrl = match.value
                            sources.add(StreamSource(
                                serverName = "VidSrc Direct",
                                url = matchUrl,
                                sourceName = name,
                                quality = "HD",
                                isM3u8 = true
                            ))
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("VidSrcScraper", "Error: ${e.message}")
        }
        
        sources.distinctBy { it.url }
    }
}
