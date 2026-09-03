package com.example.vidiio.data.scraper

import com.example.vidiio.data.model.Category
import com.example.vidiio.data.model.Movie
import com.example.vidiio.data.model.MovieType
import com.example.vidiio.data.model.Episode
import com.example.vidiio.data.model.StreamSource
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets

class VuflixScraper(private val client: OkHttpClient) : Scraper {
    override val name: String = "Vuflix"
    override val sourceId: String = "vuflix"
    override val baseUrl: String = "https://vuflix.co"

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private val referer = "https://vuflix.co/"
    private val origin = "https://vuflix.co"

    private val defaultHeaders = mapOf(
        "User-Agent" to userAgent,
        "Referer" to referer,
        "Origin" to origin,
        "Accept" to "application/json, text/plain, */*"
    )

    data class ProviderInfo(val id: String, val name: String)

    private val fallbackProviders = listOf(
        ProviderInfo("vsembed", "Sigma"),
        ProviderInfo("moonflix", "Source 40"),
        ProviderInfo("megasource", "Source 39"),
        ProviderInfo("hdghar", "Source 44"),
        ProviderInfo("moviebox", "Pi"),
        ProviderInfo("cineplay", "4K"),
        ProviderInfo("huhu", "Beta"),
        ProviderInfo("bingr", "Upsilon"),
        ProviderInfo("onlyflix", "Gamma"),
        ProviderInfo("vaplayer", "Alpha"),
        ProviderInfo("flixhqz", "Gamma"),
        ProviderInfo("castle", "Source 40"),
        ProviderInfo("cinejoy", "4K2"),
        ProviderInfo("filesun", "Tau"),
        ProviderInfo("yoru", "Yoru")
    )

    override suspend fun getHomeCategories(): List<Category> = emptyList()
    override suspend fun search(query: String): List<Movie> = emptyList()
    override suspend fun getMovieDetails(movie: Movie): Movie = movie

    override suspend fun getStreamSources(movie: Movie, episode: Episode?): List<StreamSource> = coroutineScope {
        val tmdbId = movie.id
        val isTv = movie.type == MovieType.TV_SHOW
        val type = if (isTv) "tv" else "movie"

        val providers = fetchProviders()
        val sources = mutableListOf<StreamSource>()
        val seenUrls = mutableSetOf<String>()

        val baseParams = "type=$type&tmdbId=$tmdbId" + if (isTv && episode != null) {
            "&season=${episode.seasonNumber}&episode=${episode.episodeNumber}"
        } else ""

        val deferredSources = providers.map { provider ->
            async {
                try {
                    val url = "$baseUrl/api/player/sources?$baseParams&provider=${provider.id}"
                    val request = Request.Builder()
                        .url(url)
                        .apply { defaultHeaders.forEach { (k, v) -> header(k, v) } }
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            val json = JSONObject(body)
                            if (json.optBoolean("ok") && json.has("sources")) {
                                val sourcesList = json.getJSONArray("sources")
                                val providerSources = mutableListOf<StreamSource>()
                                for (i in 0 until sourcesList.length()) {
                                    val item = sourcesList.getJSONObject(i)
                                    val providerName = item.optString("publicLabel", item.optString("providerName", provider.name))
                                    
                                    // 1. Qualities
                                    val qualities = item.optJSONArray("qualities")
                                    if (qualities != null) {
                                        for (j in 0 until qualities.length()) {
                                            val q = qualities.getJSONObject(j)
                                            val qUrl = q.optString("url")
                                            if (qUrl.isEmpty()) continue
                                            
                                            val unwrapped = unwrapUrl(qUrl)
                                            if (unwrapped.first.isEmpty() || seenUrls.contains(unwrapped.first)) continue
                                            seenUrls.add(unwrapped.first)
                                            
                                            val quality = q.optString("quality", "Auto")
                                            providerSources.add(StreamSource(
                                                serverName = "[Vuflix - $providerName] $quality",
                                                url = unwrapped.first,
                                                sourceName = name,
                                                quality = quality,
                                                headers = unwrapped.second,
                                                isM3u8 = unwrapped.first.contains(".m3u8")
                                            ))
                                        }
                                    }

                                    // 2. Primary URL
                                    val primaryUrl = item.optString("url")
                                    if (primaryUrl.isNotEmpty()) {
                                        val unwrapped = unwrapUrl(primaryUrl)
                                        if (unwrapped.first.isNotEmpty() && !seenUrls.contains(unwrapped.first)) {
                                            seenUrls.add(unwrapped.first)
                                            val quality = item.optString("quality", "HD")
                                            providerSources.add(StreamSource(
                                                serverName = "[Vuflix - $providerName] $quality",
                                                url = unwrapped.first,
                                                sourceName = name,
                                                quality = quality,
                                                headers = unwrapped.second,
                                                isM3u8 = unwrapped.first.contains(".m3u8")
                                            ))
                                        }
                                    }
                                }
                                providerSources
                            } else null
                        } else null
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }

        sources.addAll(deferredSources.awaitAll().filterNotNull().flatten())
        sources
    }

    private suspend fun fetchProviders(): List<ProviderInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/player/providers")
                .apply { defaultHeaders.forEach { (k, v) -> header(k, v) } }
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    if (json.optBoolean("ok") && json.has("providers")) {
                        val providersJson = json.getJSONArray("providers")
                        val list = mutableListOf<ProviderInfo>()
                        for (i in 0 until providersJson.length()) {
                            val p = providersJson.getJSONObject(i)
                            list.add(ProviderInfo(
                                p.getString("id"),
                                p.optString("publicLabel", p.optString("name", p.getString("id")))
                            ))
                        }
                        if (list.isNotEmpty()) return@withContext list
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VuflixScraper", "Error fetching providers: ${e.message}")
        }
        fallbackProviders
    }

    private fun unwrapUrl(rawUrl: String): Pair<String, Map<String, String>> {
        if (!rawUrl.contains("v-relay?t=") && !rawUrl.contains("a-relay?t=")) {
            return Pair(rawUrl, defaultHeaders)
        }

        try {
            val t = rawUrl.substringAfter("t=").substringBefore("&")
            if (t.isEmpty()) return Pair(rawUrl, defaultHeaders)

            val decoded = Base64.decode(t.replace("-", "+").replace("_", "/"), Base64.DEFAULT)
            val jsonStr = String(decoded, StandardCharsets.UTF_8)
            val json = JSONObject(jsonStr)
            val directUrl = json.optString("u")
            val headers = mutableMapOf<String, String>()
            headers.putAll(defaultHeaders)
            
            val h = json.optJSONObject("h")
            if (h != null) {
                h.keys().forEach { key ->
                    headers[key] = h.getString(key)
                }
            }
            
            if (directUrl.isNotEmpty()) {
                return Pair(directUrl, headers)
            }
        } catch (e: Exception) {
            Log.e("VuflixScraper", "Error unwrapping URL: ${e.message}")
        }
        return Pair(rawUrl, defaultHeaders)
    }
}
