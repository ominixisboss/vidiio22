package com.example.vidiio.data.scraper

import com.example.vidiio.data.model.Category
import com.example.vidiio.data.model.Movie
import com.example.vidiio.data.model.MovieType
import com.example.vidiio.data.model.Episode
import com.example.vidiio.data.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import android.util.Log
import android.util.Base64
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MovyScraper(private val client: OkHttpClient) : Scraper {
    override val name: String = "Movy"
    override val sourceId: String = "movy"
    override val baseUrl: String = "https://www.movy.bz/"

    private val apiBase = "https://api.wecollege.net"
    private val referer = "https://www.movy.bz/"
    private val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val defaultHeaders = mapOf(
        "User-Agent" to ua,
        "Referer" to referer,
        "Origin" to "https://www.movy.bz"
    )

    private val servers = listOf(
        mapOf("endpoint" to "miami", "name" to "Miami", "note" to "Original audio (Up to 4K)"),
        mapOf("endpoint" to "seattle", "name" to "Seattle", "note" to "Original audio"),
        mapOf("endpoint" to "denver", "name" to "Denver", "note" to "Original audio"),
        mapOf("endpoint" to "chicago", "name" to "Chicago", "note" to "Original audio"),
        mapOf("endpoint" to "dallas", "name" to "Dallas", "note" to "Original audio"),
        mapOf("endpoint" to "atlanta", "name" to "Atlanta", "note" to "Original audio"),
        mapOf("endpoint" to "houston", "name" to "Houston", "note" to "Original audio"),
        mapOf("endpoint" to "austin", "name" to "Austin", "note" to "Original audio"),
        mapOf("endpoint" to "boston", "name" to "Boston", "note" to "Original audio"),
        mapOf("endpoint" to "munich", "name" to "Munich", "note" to "German audio", "extra" to "language=german"),
        mapOf("endpoint" to "berlin", "name" to "Berlin", "note" to "German audio"),
        mapOf("endpoint" to "paris", "name" to "Paris", "note" to "French audio"),
        mapOf("endpoint" to "delhi", "name" to "Delhi", "note" to "Hindi audio"),
        mapOf("endpoint" to "cancun", "name" to "Cancun", "note" to "Spanish audio")
    )

    private val magic = byteArrayOf(109, 118, 109, 49) // "mvm1"
    private val seedCache = mutableMapOf<Int, SeedEntry>()

    private fun createRequest(url: String): Request {
        val builder = Request.Builder().url(url)
        defaultHeaders.forEach { (k, v) -> builder.header(k, v) }
        return builder.build()
    }

    override suspend fun getHomeCategories(): List<Category> = emptyList()

    override suspend fun search(query: String): List<Movie> = emptyList()

    override suspend fun getMovieDetails(movie: Movie): Movie = movie

    override suspend fun getStreamSources(movie: Movie, episode: Episode?): List<StreamSource> = withContext(Dispatchers.IO) {
        val tmdbIdStr = movie.id
        val tmdbId = tmdbIdStr.toIntOrNull() ?: return@withContext emptyList<StreamSource>()
        val isTv = movie.type == MovieType.TV_SHOW
        val mediaType = if (isTv) "tv" else "movie"

        val seed = getSeed(tmdbId) ?: return@withContext emptyList<StreamSource>()

        val encTitle = URLEncoder.encode(movie.title, "UTF-8")
        val qBuilder = StringBuilder()
        qBuilder.append("title=$encTitle")
        qBuilder.append("&mediaType=$mediaType")
        movie.year?.let { if (it > 0) qBuilder.append("&year=$it") }
        if (isTv) {
            qBuilder.append("&seasonId=${episode?.seasonNumber ?: 1}")
            qBuilder.append("&episodeId=${episode?.episodeNumber ?: 1}")
        }
        qBuilder.append("&tmdbId=$tmdbId")
        movie.imdbId?.takeIf { it.startsWith("tt") }?.let { qBuilder.append("&imdbId=$it") }
        qBuilder.append("&enc=2&seed=$seed")

        val baseQuery = qBuilder.toString()
        val seenUrls = mutableSetOf<String>()
        val sources = mutableListOf<StreamSource>()

        val deferredSources = servers.map { server ->
            async {
                val endpoint = server["endpoint"]!!
                val serverName = server["name"]!!
                val note = server["note"]!!
                val extra = server["extra"]

                var fullUrl = "$apiBase/$endpoint/sources?$baseQuery"
                if (!extra.isNullOrEmpty()) fullUrl += "&$extra"

                try {
                    val res = withTimeoutOrNull(8000.milliseconds) {
                        client.newCall(createRequest(fullUrl)).execute()
                    } ?: return@async emptyList<StreamSource>()

                    if (res.code == 200) {
                        val encText = res.body?.string()?.trim() ?: return@async emptyList<StreamSource>()
                        if (encText.isNotEmpty() && !encText.startsWith("<")) {
                            val decJsonStr = decrypt(encText, seed, tmdbId)
                            if (decJsonStr == null) {
                                Log.w("MovyScraper", "$serverName: decrypt failed (${encText.length} bytes)")
                            } else {
                                val json = JSONObject(decJsonStr)
                                val sourcesList = json.optJSONArray("sources")
                                if (sourcesList != null) {
                                    val serverSources = mutableListOf<StreamSource>()
                                    for (i in 0 until sourcesList.length()) {
                                        val src = sourcesList.optJSONObject(i) ?: continue
                                        val streamUrl = src.optString("url")
                                        if (streamUrl.isEmpty() || !seenUrls.add(streamUrl)) continue

                                        val rawQuality = src.optString("quality", "Auto")
                                        val cleanQuality = formatQuality(rawQuality)
                                        serverSources.add(StreamSource(
                                            serverName = "[Movy - $serverName] $cleanQuality ($note)",
                                            url = streamUrl,
                                            sourceName = name,
                                            quality = cleanQuality,
                                            isM3u8 = true,
                                            headers = mapOf("User-Agent" to ua, "Referer" to referer)
                                        ))
                                    }
                                    return@async serverSources
                                }
                            }
                        }
                    } else {
                        Log.w("MovyScraper", "$serverName: HTTP ${res.code}")
                    }
                } catch (e: Exception) {
                    Log.w("MovyScraper", "$serverName: ${e.message}")
                }
                emptyList<StreamSource>()
            }
        }

        sources.addAll(deferredSources.awaitAll().flatten())
        sources
    }

    private suspend fun getSeed(tmdbId: Int): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = seedCache[tmdbId]
        if (cached != null && cached.expiresAt > now + 5000) return@withContext cached.seed

        try {
            val res = client.newCall(createRequest("$apiBase/seed?mediaId=$tmdbId")).execute()
            if (res.isSuccessful) {
                val data = JSONObject(res.body?.string() ?: "")
                val seed = data.optString("seed")
                val ttlMs = data.optInt("ttlMs", 30000)
                if (seed.isNotEmpty()) {
                    seedCache[tmdbId] = SeedEntry(seed, now + ttlMs)
                    return@withContext seed
                }
            }
        } catch (e: Exception) {
            Log.e("MovyScraper", "Failed to fetch seed", e)
        }
        null
    }

    private fun formatQuality(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("2160") || lower.contains("4k") -> "4K"
            lower.contains("1080") -> "1080p"
            lower.contains("720") -> "720p"
            lower.contains("480") -> "480p"
            lower.contains("360") -> "360p"
            raw.isNotEmpty() -> raw
            else -> "Auto"
        }
    }

    // --- Decryption Cipher Implementation ---

    private fun decrypt(cipherB64: String, seed: String, tmdbId: Int): String? {
        return try {
            val normalized = cipherB64.replace('-', '+').replace('_', '/')
            val cipherBytes = Base64.decode(normalized, Base64.DEFAULT)
            if (cipherBytes.size <= magic.size) return null

            val ks = generateKeyStream(seed, tmdbId, cipherBytes.size)
            for (i in cipherBytes.indices) {
                cipherBytes[i] = (cipherBytes[i].toInt() xor ks[i].toInt()).toByte()
            }

            for (i in magic.indices) {
                if (cipherBytes[i] != magic[i]) return null
            }

            val payload = cipherBytes.sliceArray(magic.size until cipherBytes.size)
            String(payload, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun generateKeyStream(seed: String, tmdbId: Int, len: Int): ByteArray {
        val state = initKeyState(seed, tmdbId)
        val out = ByteArray(len)
        var wordIdx = 0
        var byteIdx = 0

        while (byteIdx < len) {
            val word = nextKeystreamWord(state, wordIdx++)
            out[byteIdx++] = (word and 0xFF).toByte()
            if (byteIdx < len) out[byteIdx++] = ((word ushr 8) and 0xFF).toByte()
            if (byteIdx < len) out[byteIdx++] = ((word ushr 16) and 0xFF).toByte()
            if (byteIdx < len) out[byteIdx++] = ((word ushr 24) and 0xFF).toByte()
        }
        return out
    }

    private fun initKeyState(seed: String, tmdbId: Int): KeyState {
        val s = IntArray(61)
        val isSet = BooleanArray(61)
        var r = l(fnv1a(seed) xor l((tmdbId.toLong() and 0xFFFFFFFFL).toInt() xor 0x9e3779b9.toInt()))

        for (e in 0 until 8) {
            val t = ((r.toLong() and 0xFFFFFFFFL) % 61).toInt()
            r = u((r + 0x9e3779b9.toInt()), 7 + (7 and e))
            s[t] = (r xor l(r))
            isSet[t] = true
            r = l((r + t))
        }

        val acc = l(0xa5a5a5a5.toInt() xor r)
        return KeyState(s, isSet, acc)
    }

    private fun nextKeystreamWord(state: KeyState, t: Int): Int {
        val i = ((state.acc.toLong() and 0xFFFFFFFFL) % 61).toInt()
        val oVal = if (state.isSet[i]) -1 else 0
        val d = if (state.isSet[i]) state.s[i] else 0
        val c = ((t + 1).toLong() * 0x9e3779b9L).toInt()
        val a = state.acc
        val sVal = d xor c
        val h = ((a xor sVal) or (a and sVal and oVal))
        val term1 = u((h + state.acc), 31 and i)
        val term2 = u(state.acc, 31 and (i * 7))
        val nState = l(((term1 xor term2) + 0x9e3779b9.toInt()))
        state.s[i] = nState
        state.isSet[i] = true
        state.acc = nState
        return nState
    }

    private fun l(e: Int): Int {
        var v = e
        v = v xor (v ushr 16)
        v = (v.toLong() * 0x85ebca6bL).toInt()
        v = v xor (v ushr 13)
        v = (v.toLong() * 0xc2b2ae35L).toInt()
        return v xor (v ushr 16)
    }

    private fun u(e: Int, t: Int): Int {
        val shift = t and 31
        if (shift == 0) return e
        return ((e shl shift) or (e ushr (32 - shift)))
    }

    private fun fnv1a(str: String): Int {
        var t = 0x811c9dc5.toInt()
        for (char in str) {
            t = (t xor char.code)
            t = (t.toLong() * 0x1000193L).toInt()
        }
        return l(t)
    }

    private data class SeedEntry(val seed: String, val expiresAt: Long)
    private class KeyState(val s: IntArray, val isSet: BooleanArray, var acc: Int)
}
