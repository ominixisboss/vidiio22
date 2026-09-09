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
import java.net.URLEncoder

class YtsScraper(private val client: OkHttpClient) : Scraper {
    override val name: String = "YTS"
    override val sourceId: String = "yts"
    override val baseUrl: String = "https://yts.mx"

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"

    override suspend fun getHomeCategories(): List<Category> = emptyList()
    override suspend fun search(query: String): List<Movie> = emptyList()
    override suspend fun getMovieDetails(movie: Movie): Movie = movie

    override suspend fun getStreamSources(movie: Movie, episode: Episode?): List<StreamSource> = withContext(Dispatchers.IO) {
        if (movie.type == MovieType.TV_SHOW) return@withContext emptyList() // YTS only has movies

        val sources = mutableListOf<StreamSource>()
        val query = movie.title.trim()
        val year = movie.year
        
        val apiUrl = "$baseUrl/api/v2/list_movies.json?query_term=${URLEncoder.encode(query, "UTF-8")}"

        try {
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", userAgent)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonString = response.body?.string() ?: ""
                    val json = JSONObject(jsonString)
                    val data = json.optJSONObject("data")
                    val movies = data?.optJSONArray("movies") ?: return@use

                    for (i in 0 until movies.length()) {
                        val movieObj = movies.getJSONObject(i)
                        val title = movieObj.optString("title")
                        val movieYear = movieObj.optInt("year")

                        // Verify year if possible
                        if (year != null && movieYear != 0 && movieYear != year) continue
                        
                        val torrents = movieObj.optJSONArray("torrents") ?: continue
                        for (j in 0 until torrents.length()) {
                            val torrent = torrents.getJSONObject(j)
                            val hash = torrent.optString("hash")
                            val quality = torrent.optString("quality")
                            val size = torrent.optString("size")
                            val seeds = torrent.optInt("seeds")
                            val type = torrent.optString("type") // bluray or web

                            val magnet = "magnet:?xt=urn:btih:$hash&dn=${URLEncoder.encode(title, "UTF-8")}&tr=udp://tracker.opentrackr.org:1337/announce&tr=udp://open.stealth.si:80/announce&tr=udp://tracker.torrent.eu.org:451/announce"

                            sources.add(StreamSource(
                                serverName = "$title ($quality $type)",
                                url = magnet,
                                sourceName = name,
                                quality = quality,
                                seeders = seeds,
                                size = size,
                                isM3u8 = false
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("YtsScraper", "Error: ${e.message}")
        }
        
        sources.sortedByDescending { it.seeders }
    }
}
