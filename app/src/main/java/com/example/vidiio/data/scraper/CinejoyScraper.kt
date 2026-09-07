package com.example.vidiio.data.scraper

import com.example.vidiio.utils.CryptoUtils
import com.example.vidiio.data.model.Category
import com.example.vidiio.data.model.Movie
import com.example.vidiio.data.model.MovieType
import com.example.vidiio.data.model.Episode
import com.example.vidiio.data.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import org.json.JSONArray
import android.util.Log
import java.security.PublicKey
import java.security.SecureRandom

class CinejoyScraper(private val client: OkHttpClient) : Scraper {
    override val name: String = "Cinejoy"
    override val sourceId: String = "cinejoy"
    override val baseUrl: String = "https://cinejoy.to"

    private val apiBase = "https://api.shegu.st"
    private val origin = "https://cinejoy.to"
    private val referer = "https://cinejoy.to/"
    private val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    /** Headers the CDN requires on the actual media request (ExoPlayer would 403 without them). */
    private val playbackHeaders = mapOf(
        "User-Agent" to ua,
        "Referer" to referer,
        "Origin" to origin,
    )

    private val serverPubKeyHex = "0483c7a82132b8516e3eb4061b82e9c881cc585593a4709001131bff7443eabc1701c1f0d50e23ac02b0b9a5979903dbd7e9055aab5e4a5532132d1d200707f5f2"
    private val serverPublicKey: PublicKey by lazy { CryptoUtils.decodePublicKey(serverPubKeyHex) }

    private val fallbackServers = listOf(
        mapOf("name" to "Lisbon", "4k" to true, "status" to "ok"),
        mapOf("name" to "Solara", "4k" to false, "status" to "ok"),
        mapOf("name" to "Athens", "4k" to false, "status" to "ok"),
        mapOf("name" to "Castle", "4k" to false, "status" to "ok"),
        mapOf("name" to "Canaias", "4k" to false, "status" to "ok")
    )

    private fun createRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .header("User-Agent", ua)
            .header("Origin", origin)
            .header("Referer", referer)
            .header("Accept", "application/json, text/plain, */*")
            .build()
    }

    override suspend fun getHomeCategories(): List<Category> = emptyList()

    override suspend fun search(query: String): List<Movie> = emptyList()

    override suspend fun getMovieDetails(movie: Movie): Movie = movie

    override suspend fun getStreamSources(movie: Movie, episode: Episode?): List<StreamSource> = withContext(Dispatchers.IO) {
        val tmdbId = movie.id 
        val isTv = movie.type == MovieType.TV_SHOW

        // 1. Fetch available servers
        val servers = fetchServers()

        val seenUrls = mutableSetOf<String>()
        val sources = mutableListOf<StreamSource>()

        // 2. Query all servers concurrently
        val deferredSources = servers.map { srv ->
            async {
                val srvName = srv["name"] as? String ?: return@async emptyList<StreamSource>()
                if (srv["status"] == "disabled") return@async emptyList<StreamSource>()
                if (srvName.lowercase() == "sakura" && !isTv) return@async emptyList<StreamSource>()

                val payload = JSONObject().apply {
                    put("tmdb", tmdbId)
                    if (isTv) {
                        put("season", (episode?.seasonNumber ?: 1).toString())
                        put("episode", (episode?.episodeNumber ?: 1).toString())
                    }
                }

                val targetPath = if (isTv) "/$srvName/series" else "/$srvName/movie"
                val result = executeEncryptedQuery(targetPath, payload) ?: return@async emptyList<StreamSource>()

                val data = result.optJSONObject("data") ?: result
                val streams = data.optJSONArray("stream") ?: JSONArray()
                
                val serverSources = mutableListOf<StreamSource>()
                val is4k = srv["4k"] == true

                for (i in 0 until streams.length()) {
                    val st = streams.optJSONObject(i) ?: continue
                    val stType = st.optString("type")

                    if (stType == "hls") {
                        val playlistUrl = st.optString("playlist").trim()
                        if (playlistUrl.isEmpty() || !seenUrls.add(playlistUrl)) continue

                        val quality = if (is4k) "4K / 1080p" else "Auto"
                        serverSources.add(StreamSource(
                            serverName = "[Cinejoy - $srvName] $quality",
                            url = playlistUrl,
                            sourceName = name,
                            quality = quality,
                            isM3u8 = true,
                            headers = playbackHeaders
                        ))
                    } else if (stType == "file") {
                        val qualities = st.optJSONObject("qualities") ?: continue
                        val subId = st.optString("id")

                        qualities.keys().forEach { qKey ->
                            val qVal = qualities.optJSONObject(qKey) ?: return@forEach
                            val fileUrl = qVal.optString("url").trim()
                            if (fileUrl.isEmpty() || !fileUrl.startsWith("http") || !seenUrls.add(fileUrl)) return@forEach

                            val quality = if (qKey.endsWith("p") || qKey.lowercase() == "4k") qKey else "${qKey}p"
                            val label = if (!subId.isNullOrEmpty()) "$srvName ($subId)" else srvName
                            serverSources.add(StreamSource(
                                serverName = "[Cinejoy - $label] $quality",
                                url = fileUrl,
                                sourceName = name,
                                quality = quality,
                                isM3u8 = false,
                                headers = playbackHeaders
                            ))
                        }
                    }
                }
                serverSources
            }
        }

        sources.addAll(deferredSources.awaitAll().flatten())
        sources
    }

    private suspend fun fetchServers(): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            val res = client.newCall(createRequest("$apiBase/servers")).execute()
            if (res.isSuccessful) {
                val body = res.body?.string() ?: return@withContext fallbackServers
                val json = JSONObject(body)
                val serverArray = json.optJSONArray("servers") ?: return@withContext fallbackServers
                val list = mutableListOf<Map<String, Any>>()
                for (i in 0 until serverArray.length()) {
                    val obj = serverArray.getJSONObject(i)
                    val map = mutableMapOf<String, Any>()
                    obj.keys().forEach { key -> map[key] = obj.get(key) }
                    list.add(map)
                }
                list
            } else fallbackServers
        } catch (e: Exception) {
            fallbackServers
        }
    }

    private suspend fun executeEncryptedQuery(path: String, payload: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val keyPair = CryptoUtils.generateECKeyPair()
            val clientPubBytes = CryptoUtils.getUncompressedEncoded(keyPair.public)
            val sharedSecret = CryptoUtils.computeSharedSecret(keyPair.private, serverPublicKey)

            val prk = CryptoUtils.hkdfExtract(clientPubBytes, sharedSecret)
            val reqKey = CryptoUtils.hkdfExpand(prk, "lumen-gate-v2|c2s".toByteArray(), 32)
            val resKey = CryptoUtils.hkdfExpand(prk, "lumen-gate-v2|s2c".toByteArray(), 32)

            val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
            val reqJson = JSONObject().apply {
                put("path", path)
                put("payload", payload)
            }.toString().toByteArray()

            val reqAad = "lumen-gate-v2".toByteArray() + byteArrayOf(0, 1, 1) + clientPubBytes
            val ciphertext = CryptoUtils.encryptAesGcm(reqJson, reqKey, iv, reqAad)

            val body = byteArrayOf(2, 1) + clientPubBytes + iv + ciphertext
            
            val request = Request.Builder()
                .url("$apiBase/g")
                .header("User-Agent", ua)
                .header("Origin", origin)
                .header("Referer", "$origin/watch")
                .post(body.toRequestBody("application/octet-stream".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val respBytes = response.body?.bytes() ?: return@withContext null
            if (respBytes.size <= 28) return@withContext null

            val resIv = respBytes.sliceArray(0 until 12)
            val resCiphertext = respBytes.sliceArray(12 until respBytes.size)
            val resAad = "lumen-gate-v2".toByteArray() + byteArrayOf(0, 2, 1) + clientPubBytes

            val decrypted = CryptoUtils.decryptAesGcm(resCiphertext, resKey, resIv, resAad)
            JSONObject(String(decrypted))
        } catch (e: Exception) {
            Log.e("CinejoyScraper", "Encrypted query error", e)
            null
        }
    }
}
