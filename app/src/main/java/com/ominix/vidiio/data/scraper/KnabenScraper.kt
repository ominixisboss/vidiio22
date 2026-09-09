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
import org.jsoup.Jsoup
import android.util.Log

class KnabenScraper(private val client: OkHttpClient) : Scraper {
    override val name: String = "Knaben"
    override val sourceId: String = "knaben"
    override val baseUrl: String = "https://knaben.org"
    private val mirrors = listOf("https://knaben.org", "https://knaben.eu", "https://knaben.net")

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"
    private val titleParser = TorrentTitleParser()

    override suspend fun getHomeCategories(): List<Category> = emptyList()
    override suspend fun search(query: String): List<Movie> = emptyList()
    override suspend fun getMovieDetails(movie: Movie): Movie = movie

    override suspend fun getStreamSources(movie: Movie, episode: Episode?): List<StreamSource> = withContext(Dispatchers.IO) {
        val sources = mutableListOf<StreamSource>()
        
        var query = movie.title.trim()
        if (movie.type == MovieType.MOVIE && movie.year != null) {
            query += " ${movie.year}"
        } else if (movie.type == MovieType.TV_SHOW && episode != null) {
            query += " S${"%02d".format(episode.seasonNumber)}E${"%02d".format(episode.episodeNumber)}"
        }

        var html = ""
        for (mirror in mirrors) {
            try {
                val url = "$mirror/search/${query.replace(" ", "%20")}/0/1/seeders"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        html = response.body?.string() ?: ""
                        if (html.contains("table")) return@use
                    }
                }
                if (html.isNotEmpty()) break
            } catch (e: Exception) {
                continue
            }
        }

        if (html.isEmpty()) return@withContext emptyList()

        try {
            val doc = Jsoup.parse(html)
            val rows = doc.select("table tbody tr")
            
            val searchTitleLower = movie.title.lowercase().replace(Regex("[^a-z0-9]"), "")

            for (row in rows) {
                val tds = row.select("td")
                if (tds.size < 6) continue

                val titleNode = row.select(".text-wrap.w-100 > a").first()
                val torrentName = titleNode?.text()?.trim() ?: ""
                val magnet = row.select("a[href^=magnet:]").first()?.attr("href") ?: ""

                if (torrentName.isEmpty() || magnet.isEmpty()) continue

                val size = tds[2].text().trim()
                val seeders = tds[4].text().trim().replace(",", "").toIntOrNull() ?: 0

                val parsed = titleParser.parse(torrentName)
                val cleanParsedTitle = parsed.title.lowercase().replace(Regex("[^a-z0-9]"), "")

                if (cleanParsedTitle != searchTitleLower) continue

                if (movie.type == MovieType.TV_SHOW) {
                    if (episode != null) {
                        if (parsed.season != null && parsed.season != episode.seasonNumber) continue
                        if (parsed.episode != null && parsed.episode != episode.episodeNumber) continue
                    }
                }

                sources.add(StreamSource(
                    serverName = torrentName,
                    url = magnet,
                    sourceName = name,
                    quality = parsed.resolution ?: "HD",
                    seeders = seeders,
                    size = size,
                    isM3u8 = false
                ))
            }
        } catch (e: Exception) {
            Log.e("KnabenScraper", "Error: ${e.message}")
        }
        
        sources.sortedByDescending { it.seeders }
    }
}
