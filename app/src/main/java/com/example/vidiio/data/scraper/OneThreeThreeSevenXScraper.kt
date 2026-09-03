package com.example.vidiio.data.scraper

import com.example.vidiio.data.model.Category
import com.example.vidiio.data.model.Movie
import com.example.vidiio.data.model.MovieType
import com.example.vidiio.data.model.Episode
import com.example.vidiio.data.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import android.util.Log

class OneThreeThreeSevenXScraper(private val client: OkHttpClient) : Scraper {
    override val name: String = "1337x"
    override val sourceId: String = "1337x"
    override val baseUrl: String = "https://1337x.to"
    
    private val mirrors = listOf("https://1337x.to", "https://1337x.so", "https://1337x.st", "https://x1337x.se")
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"
    private val titleParser = TorrentTitleParser()

    override suspend fun getHomeCategories(): List<Category> = emptyList()
    override suspend fun search(query: String): List<Movie> = emptyList()
    override suspend fun getMovieDetails(movie: Movie): Movie = movie

    override suspend fun getStreamSources(movie: Movie, episode: Episode?): List<StreamSource> = coroutineScope {
        val sources = mutableListOf<StreamSource>()
        
        var query = movie.title.trim()
        if (movie.type == MovieType.MOVIE && movie.year != null) {
            query += " ${movie.year}"
        } else if (movie.type == MovieType.TV_SHOW && episode != null) {
            query += " S${"%02d".format(episode.seasonNumber)}E${"%02d".format(episode.episodeNumber)}"
        }

        var currentBaseUrl = baseUrl
        var html = ""
        
        for (mirror in mirrors) {
            try {
                val searchUrl = "$mirror/search/${query.replace(" ", "%20")}/1/"
                val request = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", userAgent)
                    .build()

                html = withContext(Dispatchers.IO) {
                    client.newCall(request).execute().use { it.body?.string() ?: "" }
                }
                if (html.contains("table-list")) {
                    currentBaseUrl = mirror
                    break
                }
            } catch (e: Exception) {
                continue
            }
        }

        if (html.isEmpty()) return@coroutineScope emptyList()

        try {
            val doc = Jsoup.parse(html)
            val rows = doc.select("table.table-list tbody tr")
            
            val searchTitleLower = movie.title.lowercase().replace(Regex("[^a-z0-9]"), "")

            val deferredSources = rows.take(15).map { row ->
                async {
                    val nameCell = row.select("td.coll-1.name a").lastOrNull()
                    val torrentName = nameCell?.text()?.trim() ?: ""
                    val detailPath = nameCell?.attr("href") ?: ""
                    
                    if (torrentName.isEmpty() || detailPath.isEmpty()) return@async null

                    val seeders = row.select("td.coll-2.seeds").first()?.text()?.trim()?.toIntOrNull() ?: 0
                    val sizeText = row.select("td.coll-4.size").first()?.text()?.trim() ?: "Unknown"
                    // Size on 1337x often includes "<span>" or is just text. Jsoup .text() should handle it.
                    val size = sizeText.substringBefore("B") + "B"

                    val parsed = titleParser.parse(torrentName)
                    val cleanParsedTitle = parsed.title.lowercase().replace(Regex("[^a-z0-9]"), "")

                    if (cleanParsedTitle != searchTitleLower) return@async null

                    if (movie.type == MovieType.TV_SHOW && episode != null) {
                        if (parsed.season != null && parsed.season != episode.seasonNumber) return@async null
                        if (parsed.episode != null && parsed.episode != episode.episodeNumber) return@async null
                    }

                    fetchMagnet("$currentBaseUrl$detailPath")?.let { magnet ->
                        StreamSource(
                            serverName = torrentName,
                            url = magnet,
                            sourceName = name,
                            quality = parsed.resolution ?: "HD",
                            seeders = seeders,
                            size = size,
                            isM3u8 = false
                        )
                    }
                }
            }
            sources.addAll(deferredSources.awaitAll().filterNotNull())
        } catch (e: Exception) {
            Log.e("1337xScraper", "Error: ${e.message}")
        }
        
        sources.sortedByDescending { it.seeders }
    }

    private suspend fun fetchMagnet(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    val doc = Jsoup.parse(html)
                    doc.select("a[href^=magnet:]").first()?.attr("href")
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
