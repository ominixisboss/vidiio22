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
import java.util.*

class VidEasyScraper(private val client: OkHttpClient) : Scraper {
    override val name: String = "VidEasy"
    override val sourceId: String = "videasy"
    override val baseUrl: String = "https://api.speedracelight.com"

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private val referer = "https://player.videasy.to/"
    private val origin = "https://player.videasy.to"

    private val providers = listOf(
        mapOf("path" to "/cdn/sources-with-title", "label" to "Yoru"),
        mapOf("path" to "/neon2/sources-with-title", "label" to "Neon"),
        mapOf("path" to "/m4uhd/sources-with-title", "label" to "Breach"),
        mapOf("path" to "/meine/sources-with-title", "label" to "Killjoy"),
        mapOf("path" to "/lamovie/sources-with-title", "label" to "Omen")
    )

    private val f = longArrayOf(
        1116352408L, 1899447441L, 3049323471L, 3921009573L, 961987163L, 1508970993L,
        2453635748L, 2870763221L, 3624381080L, 310598401L, 607225278L, 1426881987L,
        1925078388L, 2162078206L, 2614888103L, 3248222580L
    )

    private val magic = byteArrayOf(109, 118, 109, 49) // "mvm1"

    override suspend fun getHomeCategories(): List<Category> = emptyList()
    override suspend fun search(query: String): List<Movie> = emptyList()
    override suspend fun getMovieDetails(movie: Movie): Movie = movie

    override suspend fun getStreamSources(movie: Movie, episode: Episode?): List<StreamSource> = coroutineScope {
        val sources = mutableListOf<StreamSource>()
        val tmdbId = movie.id.toIntOrNull() ?: return@coroutineScope emptyList()
        val isTv = movie.type == MovieType.TV_SHOW

        try {
            val seed = fetchSeed(tmdbId) ?: return@coroutineScope emptyList()
            
            val params = mutableMapOf(
                "title" to movie.title,
                "mediaType" to if (isTv) "tv" else "movie",
                "tmdbId" to tmdbId.toString(),
                "enc" to "2",
                "seed" to seed
            )
            movie.year?.let { params["year"] = it.toString() }
            if (isTv && episode != null) {
                params["seasonId"] = episode.seasonNumber.toString()
                params["episodeId"] = episode.episodeNumber.toString()
            }

            val deferredSources = providers.map { provider ->
                async {
                    try {
                        val path = provider["path"] ?: ""
                        val label = provider["label"] ?: ""
                        
                        val urlBuilder = StringBuilder("$baseUrl$path?")
                        params.forEach { (k, v) -> urlBuilder.append("$k=${v.replace(" ", "%20")}&") }
                        
                        val request = Request.Builder()
                            .url(urlBuilder.toString().removeSuffix("&"))
                            .header("User-Agent", userAgent)
                            .header("Referer", referer)
                            .header("Origin", origin)
                            .build()

                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                var body = response.body?.string()?.trim() ?: ""
                                if (body.startsWith("\"") && body.endsWith("\"")) {
                                    body = body.substring(1, body.length - 1)
                                }
                                val decryptedJson = decryptPayload(body, seed, tmdbId)
                                val data = JSONObject(decryptedJson)
                                val rawSources = data.optJSONArray("sources")
                                val result = mutableListOf<StreamSource>()
                                if (rawSources != null) {
                                    for (i in 0 until rawSources.length()) {
                                        val s = rawSources.getJSONObject(i)
                                        val streamUrl = s.optString("url", s.optString("file"))
                                        if (streamUrl.isEmpty() || !streamUrl.startsWith("http")) continue
                                        val q = s.optString("quality", "Auto")
                                        result.add(StreamSource(
                                            serverName = "Videasy $label · $q",
                                            url = streamUrl,
                                            sourceName = name,
                                            quality = q,
                                            headers = mapOf("User-Agent" to userAgent, "Referer" to referer),
                                            isM3u8 = streamUrl.contains(".m3u8")
                                        ))
                                    }
                                }
                                result
                            } else null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            sources.addAll(deferredSources.awaitAll().filterNotNull().flatten())

        } catch (e: Exception) {
            Log.e("VidEasyScraper", "Error: ${e.message}")
        }

        sources
    }

    private suspend fun fetchSeed(mediaId: Int): String? = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/seed?mediaId=$mediaId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Referer", referer)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    json.optString("seed")
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    // Decryption logic
    private fun imul(a: Long, b: Long): Long = ((a and 0xFFFFL) * b + (((a ushr 16) * b and 0xFFFFL) shl 16)) and 0xFFFFFFFFL
    private fun isEvenTri(e: Int): Boolean = (((e * (e + 1)) and 1) == 0)
    private fun isOddTri(e: Int): Boolean = (((e * (e + 1)) and 1) == 1)

    private fun mix(e: Long): Long {
        var res = e and 0xFFFFFFFFL
        res = res xor (res ushr 16)
        res = imul(res, 2246822507L) and 0xFFFFFFFFL
        res = res xor (res ushr 13)
        res = imul(res, 3266489909L) and 0xFFFFFFFFL
        return (res xor (res ushr 16)) and 0xFFFFFFFFL
    }

    private fun rotl(e: Long, t: Int): Long {
        val res = e and 0xFFFFFFFFL
        val rot = t and 31
        if (rot == 0) return res
        return (((res shl rot) and 0xFFFFFFFFL) or (res ushr (32 - rot))) and 0xFFFFFFFFL
    }

    private fun fnv1a(e: String): Long {
        var t = 2166136261L
        for (i in e.indices) {
            t = imul(t xor e[i].code.toLong(), 16777619L) and 0xFFFFFFFFL
        }
        return mix(t)
    }

    private fun accSeed(e: String): Long {
        var t = 1732584193L
        for (i in e.indices) {
            t = rotl((t xor imul(e[i].code.toLong(), f[15 and i])) and 0xFFFFFFFFL, 5)
        }
        return mix(t)
    }

    private fun rc4Sbox(e: String): IntArray {
        val t = IntArray(256) { it }
        var s = 0
        for (a in 0 until 256) {
            s = (s + t[a] + e[a % e.length].code) and 255
            val r = t[a]
            t[a] = t[s]
            t[s] = r
        }
        return t
    }

    private fun buildState(seed: String, mediaId: Int): Pair<Any, Long> {
        if (isOddTri(seed.length)) {
            return Pair(rc4Sbox(seed), accSeed(seed))
        }
        val s = LongArray(61) { -1L }
        var a = mix(fnv1a(seed) xor mix((mediaId.toLong() and 0xFFFFFFFFL) xor 2654435769L)) and 0xFFFFFFFFL
        for (e in 0 until 8) {
            if (isEvenTri(e)) {
                val t = (a % 61).toInt()
                a = rotl((a + 2654435769L) and 0xFFFFFFFFL, 7 + (7 and e))
                s[t] = (a xor mix(a)) and 0xFFFFFFFFL
                a = mix((a + t.toLong()) and 0xFFFFFFFFL)
            } else {
                s[e] = f[15 and e]
            }
        }
        return Pair(s, mix(2779096485L xor a) and 0xFFFFFFFFL)
    }

    private fun nextWord(state: Pair<Any, Long>, counter: Int): Pair<Long, Long> {
        val r = state.first
        var acc = state.second
        val n = (acc % 61).toInt()
        
        val l = if (r is IntArray) {
            (r[n % 256].toLong()) and 0xFFFFFFFFL
        } else {
            val ra = r as LongArray
            if (ra[n] != -1L) ra[n] else 0L
        }
        
        val exists = if (r is IntArray) true else (r as LongArray)[n] != -1L
        val i = if (exists) -1L else 0L
        
        val a = (l xor (imul(2654435769L, (counter + 1).toLong()) and 0xFFFFFFFFL)) and 0xFFFFFFFFL
        var d = (((acc xor a) and 0xFFFFFFFFL) or (((acc and a) and i) and 0xFFFFFFFFL)) and 0xFFFFFFFFL
        d = (rotl((d + acc) and 0xFFFFFFFFL, 31 and n) xor rotl(acc, 31 and (n * 7))) and 0xFFFFFFFFL
        acc = mix((d + 2654435769L) and 0xFFFFFFFFL)
        
        if (r is IntArray) {
            r[n % 256] = (acc and 0xFFFFFFFFL).toInt()
        } else {
            (r as LongArray)[n] = acc and 0xFFFFFFFFL
        }
        
        return Pair(acc and 0xFFFFFFFFL, acc)
    }

    private fun keystream(seed: String, mediaId: Int, len: Int): ByteArray {
        val res = buildState(seed, mediaId)
        val s = res.first
        var acc = res.second
        val out = ByteArray(len)
        var counter = 0
        var e = 0
        while (e < len) {
            val nw = nextWord(Pair(s, acc), counter++)
            val t = nw.first
            acc = nw.second
            out[e++] = (t and 255L).toByte()
            if (e < len) out[e++] = ((t shr 8) and 255L).toByte()
            if (e < len) out[e++] = ((t shr 16) and 255L).toByte()
            if (e < len) out[e++] = ((t shr 24) and 255L).toByte()
        }
        return out
    }

    private fun decryptPayload(payload: String, seed: String, mediaId: Int): String {
        val r = Base64.decode(payload.replace("-", "+").replace("_", "/"), Base64.DEFAULT)
        val o = keystream(seed, mediaId, r.size)
        val decrypted = ByteArray(r.size)
        for (i in r.indices) {
            decrypted[i] = (r[i].toInt() xor o[i].toInt()).toByte()
        }
        for (i in magic.indices) {
            if (decrypted[i] != magic[i]) {
                throw Exception("Videasy decrypt failed")
            }
        }
        return String(decrypted, magic.size, decrypted.size - magic.size, StandardCharsets.UTF_8)
    }
}
