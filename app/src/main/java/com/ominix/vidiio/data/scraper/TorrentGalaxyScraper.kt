package com.ominix.vidiio.data.scraper

import com.ominix.vidiio.data.model.Category
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.MovieType
import com.ominix.vidiio.data.model.Episode
import com.ominix.vidiio.data.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import android.util.Log
import kotlinx.coroutines.CancellationException

class TorrentGalaxyScraper(private val client: OkHttpClient) : Scraper {
    override val name: String = "TorrentGalaxy"
    override val sourceId: String = "tg"
    override val baseUrl: String = "https://torrentgalaxy.to"
    private val mirrors = listOf("https://torrentgalaxy.to", "https://torrentgalaxy.info", "https://torrentgalaxy.mx")

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
                val searchUrl = "$mirror/get-posts/keywords:${query.replace(" ", "%20")}"
                val request = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", userAgent)
                    .build()

                html = withContext(Dispatchers.IO) {
                    client.newCall(request).execute().use { it.body?.string() ?: "" }
                }
                if (html.contains("tgxtable")) {
                    currentBaseUrl = mirror
                    break
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "TorrentGalaxyScraper.getStreamSources() failed", e)
                continue
            }
        }

        if (html.isEmpty()) return@coroutineScope emptyList()

        try {
            val doc = Jsoup.parse(html)
            val rows = doc.select(".tgxtable .tgxtablerow")
            
            val searchTitleLower = movie.title.lowercase().replace(Regex("[^a-z0-9]"), "")

            val deferredSources = rows.map { row ->
                async {
                    val titleNode = row.select(".tgxtablecell.clickable-row a[title]").first()
                    val torrentName = titleNode?.attr("title")?.trim() ?: ""
                    val postUrlPath = titleNode?.attr("href") ?: ""
                    
                    if (torrentName.isEmpty() || postUrlPath.isEmpty()) return@async null

                    val size = row.select(".badge-secondary").first()?.text()?.trim() ?: "Unknown"
                    val seedersFonts = row.select("font[color=green] b")
                    val seeders = if (seedersFonts.isNotEmpty()) seedersFonts.first()?.text()?.trim()?.toIntOrNull() ?: 0 else 0

                    val parsed = titleParser.parse(torrentName)
                    val cleanParsedTitle = parsed.title.lowercase().replace(Regex("[^a-z0-9]"), "")

                    if (cleanParsedTitle != searchTitleLower) return@async null

                    if (movie.type == MovieType.TV_SHOW) {
                        if (episode != null) {
                            if (parsed.season != null && parsed.season != episode.seasonNumber) return@async null
                            if (parsed.episode != null && parsed.episode != episode.episodeNumber) return@async null
                        }
                    }

                    fetchMagnet("$currentBaseUrl$postUrlPath")?.let { magnet ->
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
            if (e is CancellationException) throw e
            Log.e("TorrentGalaxyScraper", "Error: ${e.message}")
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
            if (e is CancellationException) throw e
            Log.w(TAG, "TorrentGalaxyScraper.fetchMagnet() failed", e)
            null
        }
    }
}

private const val TAG = "TorrentGalaxyScraper"