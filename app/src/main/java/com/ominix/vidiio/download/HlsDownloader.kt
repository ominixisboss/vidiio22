package com.ominix.vidiio.download

import android.util.Log
import com.ominix.vidiio.data.model.DownloadTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Downloads an HLS (.m3u8) VOD by fetching every segment and concatenating them into one
 * MPEG-TS file. Handles master playlists (picks the highest-bandwidth variant) and
 * AES-128 segment encryption. Not a perfect remux, but the result plays.
 */
class HlsDownloader(private val client: OkHttpClient) {

    suspend fun download(
        task: DownloadTask,
        destDir: File,
        onProgress: (progress: Float, downloaded: Long, total: Long) -> Unit,
        isCancelled: () -> Boolean
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val headers = parseHeaders(task.headersJson)

            var playlistUrl = task.url
            var playlist = fetchText(playlistUrl, headers)
                ?: return@withContext DownloadResult.Error("Couldn't fetch playlist")

            // Master playlist -> pick the best variant.
            if (playlist.contains("#EXT-X-STREAM-INF")) {
                val variant = pickBestVariant(playlist, playlistUrl)
                    ?: return@withContext DownloadResult.Error("No variant in master playlist")
                playlistUrl = variant
                playlist = fetchText(playlistUrl, headers)
                    ?: return@withContext DownloadResult.Error("Couldn't fetch variant playlist")
            }

            val segments = mutableListOf<String>()
            var keyUri: String? = null
            var keyIvHex: String? = null
            playlist.lineSequence().forEach { raw ->
                val line = raw.trim()
                when {
                    line.startsWith("#EXT-X-KEY") -> {
                        val method = Regex("METHOD=([A-Z0-9-]+)").find(line)?.groupValues?.get(1)
                        if (method == "AES-128") {
                            keyUri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
                                ?.let { resolve(it, playlistUrl) }
                            keyIvHex = Regex("IV=0x([0-9A-Fa-f]+)").find(line)?.groupValues?.get(1)
                        } else {
                            keyUri = null; keyIvHex = null
                        }
                    }
                    line.isNotEmpty() && !line.startsWith("#") -> segments.add(resolve(line, playlistUrl))
                }
            }
            if (segments.isEmpty()) return@withContext DownloadResult.Error("No segments in playlist")

            val key: ByteArray? = keyUri?.let { fetchBytes(it, headers) }

            val safeTitle = task.title
                .replace(Regex("\\.(mp4|mkv|webm|ts|m3u8)$", RegexOption.IGNORE_CASE), "")
                .replace(Regex("[^a-zA-Z0-9.\\- ]"), "_").trim().ifEmpty { "video" }
            val out = File(destDir, "$safeTitle.ts")
            FileOutputStream(out).use { fos ->
                var written = 0L
                segments.forEachIndexed { i, segUrl ->
                    if (isCancelled()) return@withContext DownloadResult.Cancelled
                    var bytes = fetchBytes(segUrl, headers)
                        ?: return@withContext DownloadResult.Error("Segment ${i + 1} failed")
                    if (key != null) {
                        val iv = keyIvHex?.let { hexToBytes(it) } ?: segmentIv(i)
                        bytes = aes128Decrypt(bytes, key, iv)
                    }
                    fos.write(bytes)
                    written += bytes.size
                    onProgress((i + 1f) / segments.size * 100f, written, -1L)
                }
            }
            Log.d(TAG, "HLS download done: ${out.name} (${out.length()} bytes, ${segments.size} segments)")
            DownloadResult.Success(out.absolutePath, out.length())
        } catch (e: Exception) {
            DownloadResult.Error(e.message ?: "HLS download error")
        }
    }

    private fun pickBestVariant(master: String, baseUrl: String): String? {
        var bestBw = -1L
        var bestUri: String? = null
        val lines = master.lines()
        for (i in lines.indices) {
            if (lines[i].startsWith("#EXT-X-STREAM-INF")) {
                val bw = Regex("BANDWIDTH=(\\d+)").find(lines[i])?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val uri = lines.getOrNull(i + 1)?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("#") }
                if (uri != null && bw > bestBw) { bestBw = bw; bestUri = resolve(uri, baseUrl) }
            }
        }
        return bestUri
    }

    private fun resolve(ref: String, base: String): String =
        if (ref.startsWith("http")) ref else runCatching { URI(base).resolve(ref).toString() }.getOrDefault(ref)

    private fun request(url: String, headers: Map<String, String>): Request {
        val b = Request.Builder().url(url).header("User-Agent", UA)
        headers.forEach { (k, v) -> b.header(k, v) }
        return b.build()
    }

    private fun fetchText(url: String, headers: Map<String, String>): String? =
        runCatching { client.newCall(request(url, headers)).execute().use { if (it.isSuccessful) it.body?.string() else null } }.getOrNull()

    private fun fetchBytes(url: String, headers: Map<String, String>): ByteArray? =
        runCatching { client.newCall(request(url, headers)).execute().use { if (it.isSuccessful) it.body?.bytes() else null } }.getOrNull()

    private fun aes128Decrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    private fun segmentIv(index: Int): ByteArray = ByteArray(16).also {
        var v = index
        for (i in 15 downTo 0) { it[i] = (v and 0xFF).toByte(); v = v ushr 8 }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.removePrefix("0x").let { if (it.length % 2 == 1) "0$it" else it }
        return ByteArray(clean.length / 2) { ((clean[it * 2].digitToInt(16) shl 4) or clean[it * 2 + 1].digitToInt(16)).toByte() }
    }

    private fun parseHeaders(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val o = JSONObject(json)
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
    }

    companion object {
        private const val TAG = "HlsDownloader"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
