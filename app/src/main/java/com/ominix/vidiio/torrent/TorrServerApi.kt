package com.ominix.vidiio.torrent

import android.net.Uri
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * Thin typed client for the TorrServer REST API, mirroring
 * `torrserver_flutter`'s `TorrServerRestClient` (lib/src/rest_client.dart).
 *
 * All control calls use short timeouts; the actual video is streamed by
 * ExoPlayer straight from [streamUrl], not through this client.
 */
class TorrServerApi(private val baseUrl: String) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val moshi = Moshi.Builder().build()
    private val torrentAdapter = moshi.adapter(TsTorrent::class.java)
    private val torrentListAdapter =
        moshi.adapter<List<TsTorrent>>(Types.newParameterizedType(List::class.java, TsTorrent::class.java))

    /** GET /echo — returns the server version string, or null if unreachable. */
    fun echo(timeoutMs: Long = 1500): String? = try {
        val c = client.newBuilder()
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
        c.newCall(Request.Builder().url("$baseUrl/echo").build()).execute().use { r ->
            if (r.isSuccessful) r.body.string().trim() else null
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w(TAG, "TorrServerApi failed", e)
        null
    }

    /** GET /shutdown — asks the server to terminate gracefully. */
    fun shutdown() {
        runCatching {
            client.newBuilder().callTimeout(1, TimeUnit.SECONDS).build()
                .newCall(Request.Builder().url("$baseUrl/shutdown").build())
                .execute().close()
        }
    }

    /** POST /torrents {action:"add"} — adds a magnet / hash and returns its status. */
    fun addTorrent(link: String, title: String? = null): TsTorrent? {
        val body = JSONObject().apply {
            put("action", "add")
            put("link", link)
            if (!title.isNullOrEmpty()) put("title", title)
            put("save_to_db", false)
        }
        return postTorrents(body)?.let { parseTorrent(it) }
    }

    /** POST /torrents {action:"get"} — status + file list for one torrent. */
    fun getTorrent(hash: String): TsTorrent? {
        val body = JSONObject().apply {
            put("action", "get")
            put("hash", hash)
        }
        return postTorrents(body)?.let { parseTorrent(it) }
    }

    /** POST /torrents {action:"drop"} — frees the torrent from RAM (keeps DB entry). */
    fun dropTorrent(hash: String) = fireTorrents("drop", hash)

    /** POST /torrents {action:"rem"} — removes the torrent entirely. */
    fun removeTorrent(hash: String) = fireTorrents("rem", hash)

    /**
     * Playable HTTP URL served by TorrServer with full range support.
     * Mirrors rest_client.dart: `/stream/<name>?link=<hash>&index=<id>&play`.
     */
    fun streamUrl(hash: String, fileIndex: Int, fileName: String? = null): String {
        val index = if (fileIndex > 0) fileIndex else 1
        val name = (fileName?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "stream")
        return Uri.parse(baseUrl).buildUpon()
            .appendPath("stream")
            .appendPath(name)
            .appendQueryParameter("link", hash)
            .appendQueryParameter("index", index.toString())
            .appendQueryParameter("play", "")
            .build()
            .toString()
    }

    private fun fireTorrents(action: String, hash: String) {
        runCatching {
            postTorrents(JSONObject().apply {
                put("action", action)
                put("hash", hash)
            })
        }
    }

    private fun postTorrents(body: JSONObject): String? {
        val req = Request.Builder()
            .url("$baseUrl/torrents")
            .post(body.toString().toRequestBody(JSON))
            .build()
        return try {
            client.newCall(req).execute().use { r ->
                val text = r.body.string()
                if (!r.isSuccessful) {
                    Log.w(TAG, "POST /torrents ${body.optString("action")} -> ${r.code}: $text")
                    null
                } else {
                    text
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "POST /torrents ${body.optString("action")} failed: ${e.message}")
            null
        }
    }

    private fun parseTorrent(json: String): TsTorrent? = try {
        val trimmed = json.trimStart()
        if (trimmed.startsWith("[")) {
            torrentListAdapter.fromJson(json)?.firstOrNull()
        } else {
            torrentAdapter.fromJson(json)
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w(TAG, "Failed to parse torrent JSON: ${e.message}")
        null
    }

    companion object {
        private const val TAG = "TorrServerApi"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
