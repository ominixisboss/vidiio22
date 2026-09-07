package com.example.vidiio.torrent

import android.content.Context
import android.util.Log
import com.example.vidiio.VidiioApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Facade over the embedded [TorrServerEngine], reproducing PlayTorrioV3's
 * `TorrentStreamService` flow (lib/services/stream/torrent_stream_service.dart):
 *
 *  1. start the engine, `addTorrent(magnet)`
 *  2. poll `getTorrent(hash)` every 300ms until `file_stats` is populated (metadata)
 *  3. pick the right file, hand ExoPlayer the TorrServer `/stream` URL
 *  4. poll stats once a second for the peers / speed / buffer overlay
 *
 * The public API (status flow, [getMetadata], [startStreaming], [stop] and the
 * [TorrentStatus] / [TorrentFileInfo] models) is unchanged so the player UI is untouched,
 * except [startStreaming] now returns the stream URL directly instead of a local File.
 */
class TorrentManager(private val context: Context) {

    private val app: VidiioApplication
        get() = context.applicationContext as VidiioApplication

    private val engine: TorrServerEngine get() = app.torrServerEngine

    private val titleParser = TorrentTitleParser()

    private var hash: String? = null
    private var selectedFile: TsFileStat? = null

    var targetFileSize: Long = 0L
        private set

    private val _status = MutableStateFlow<TorrentStatus?>(null)
    val status: StateFlow<TorrentStatus?> = _status

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollJob: Job? = null

    /** Whether the TorrServer engine binary is bundled for this device's ABI. */
    val isAvailable: Boolean get() = engine.isInstalled

    // ─────────────────────────────────────────────────────────────────────────
    // Metadata
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun getMetadata(magnetUrl: String): List<TorrentFileInfo>? = withContext(Dispatchers.IO) {
        _status.value = TorrentStatus(0f, 0f, 0f, 0, 0, 0f, "Starting engine...")

        if (!engine.ensureStarted()) {
            _status.value = _status.value?.copy(statusMessage = "Torrent engine failed to start")
            return@withContext null
        }
        val api = engine.getApi() ?: return@withContext null

        val magnet = normalizeMagnet(magnetUrl)
        _status.value = _status.value?.copy(statusMessage = "Adding torrent...")

        val added = try {
            api.addTorrent(magnet, extractDisplayName(magnet))
        } catch (e: Exception) {
            Log.e(TAG, "addTorrent failed", e)
            null
        }
        if (added == null) {
            _status.value = _status.value?.copy(statusMessage = "Failed to add torrent")
            return@withContext null
        }

        val h = added.hash.ifEmpty { extractHash(magnet).orEmpty() }.lowercase()
        if (h.isEmpty()) {
            _status.value = _status.value?.copy(statusMessage = "Invalid magnet link")
            return@withContext null
        }
        hash = h
        Log.d(TAG, "Torrent added: $h. Waiting for metadata...")
        _status.value = _status.value?.copy(statusMessage = "Finding Peers for Metadata...")
        startPolling(h)

        val info = waitForMetadata(api, h)
        if (info == null || info.fileStats.isEmpty()) {
            _status.value = _status.value?.copy(statusMessage = "Metadata fetching timed out")
            return@withContext null
        }

        _status.value = _status.value?.copy(statusMessage = "Metadata loaded")
        info.fileStats.map { fs ->
            val name = fs.path.substringAfterLast('/')
            val parsed = titleParser.parse(name)
            TorrentFileInfo(
                index = fs.id,
                name = name,
                path = fs.path,
                size = fs.length,
                isMedia = isMediaFile(name),
                season = parsed.season,
                episode = parsed.episode,
            )
        }
    }

    private suspend fun waitForMetadata(
        api: TorrServerApi,
        hash: String,
        timeoutMs: Long = 45_000L,
        pollMs: Long = 300L,
    ): TsTorrent? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var info: TsTorrent? = null
        while (System.currentTimeMillis() < deadline) {
            info = try {
                api.getTorrent(hash)
            } catch (e: Exception) {
                Log.w(TAG, "metadata poll error: ${e.message}")
                null
            }
            if (info != null && info.fileStats.isNotEmpty()) return info
            delay(pollMs)
        }
        return info
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Streaming
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns the TorrServer HTTP stream URL for the selected file, or null on failure. */
    suspend fun startStreaming(
        fileIndex: Int = -1,
        season: Int? = null,
        episode: Int? = null,
        fileName: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        val h = hash ?: return@withContext null
        val api = engine.getApi() ?: return@withContext null

        val info = try {
            api.getTorrent(h)
        } catch (e: Exception) {
            null
        } ?: return@withContext null

        // An addon-supplied filename is the most reliable selector for season packs.
        val wantedName = fileName?.substringAfterLast('/')?.trim()
        val file = wantedName?.let { n ->
            info.fileStats.firstOrNull { fs ->
                fs.path.substringAfterLast('/').equals(n, ignoreCase = true) ||
                    fs.path.endsWith(n, ignoreCase = true)
            }
        }
            ?: MediaFileSelector.select(
                files = info.fileStats,
                season = season,
                episode = episode,
                preferredId = fileIndex.takeIf { it >= 1 },
            )
        if (file == null) {
            _status.value = _status.value?.copy(statusMessage = "No playable file in torrent")
            return@withContext null
        }

        selectedFile = file
        targetFileSize = file.length
        startPolling(h)
        _status.value = _status.value?.copy(statusMessage = "Buffering...")

        val url = api.streamUrl(h, file.id, file.path)
        Log.d(TAG, "Streaming file #${file.id} '${file.path}' -> $url")
        url
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stats polling
    // ─────────────────────────────────────────────────────────────────────────

    private fun startPolling(hash: String) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                val api = engine.getApi()
                val t = api?.let { runCatching { it.getTorrent(hash) }.getOrNull() }
                if (t != null) {
                    val total = if (t.torrentSize > 0) t.torrentSize else targetFileSize
                    val buffer = when {
                        t.preloadSize > 0 -> t.preloadedBytes.toFloat() / t.preloadSize * 100f
                        total > 0 -> t.loadedSize.toFloat() / total * 100f
                        else -> 0f
                    }.coerceIn(0f, 100f)

                    _status.value = TorrentStatus(
                        progress = if (total > 0) (t.loadedSize.toFloat() / total * 100f).coerceIn(0f, 100f) else 0f,
                        downloadRate = (t.downloadSpeed / 1024.0).toFloat(),
                        uploadRate = (t.uploadSpeed / 1024.0).toFloat(),
                        numSeeders = t.connectedSeeders,
                        numPeers = t.activePeers,
                        bufferProgress = buffer,
                        statusMessage = liveStatusMessage(t),
                    )
                }
                delay(1000)
            }
        }
    }

    private fun liveStatusMessage(t: TsTorrent): String {
        val current = _status.value?.statusMessage
        // Keep terminal / explicit messages set elsewhere.
        if (current != null && (current.startsWith("Failed") || current.startsWith("No playable") ||
                current.startsWith("Invalid") || current.contains("timed out"))
        ) return current

        return when {
            !t.hasMetadata -> "Finding Peers for Metadata..."
            t.activePeers == 0 && t.downloadSpeed == 0.0 -> "Connecting to Peers..."
            t.preloadSize > 0 && t.preloadedBytes < t.preloadSize -> "Buffering..."
            else -> "Ready to Stream"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        val h = hash
        hash = null
        selectedFile = null
        targetFileSize = 0L
        _status.value = null
        // Fire-and-forget on a detached scope so cancellation below doesn't kill it.
        // TorrServer is only used for streaming, so it's safe to shut down with the player.
        CoroutineScope(Dispatchers.IO).launch {
            if (h != null) runCatching { engine.getApi()?.dropTorrent(h) }
            runCatching { engine.stop() }
        }
        scope.coroutineContext[Job]?.cancelChildren()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun isMediaFile(name: String): Boolean {
        val extensions = listOf(".mp4", ".mkv", ".avi", ".mov", ".flv", ".wmv", ".m4v", ".webm", ".ts")
        return extensions.any { name.endsWith(it, true) }
    }

    private fun normalizeMagnet(input: String): String {
        val raw = input.trim()
        if (raw.startsWith("magnet:?", ignoreCase = true)) return raw
        val hash = extractHash(raw)
        return if (hash != null) "magnet:?xt=urn:btih:$hash" else raw
    }

    private fun extractHash(magnetOrHash: String): String? {
        // Prefer the value right after btih: so a long dn= name can't be mistaken for a hash.
        BTIH_40.find(magnetOrHash)?.let { return it.groupValues[1].lowercase() }
        BTIH_32.find(magnetOrHash)?.let { return base32ToHex(it.groupValues[1]) }
        val trimmed = magnetOrHash.trim()
        if (trimmed.matches(HASH_40)) return trimmed.lowercase()
        if (trimmed.matches(HASH_32)) return base32ToHex(trimmed)
        return null
    }

    private fun extractDisplayName(magnet: String): String? {
        val m = Regex("[?&]dn=([^&]+)").find(magnet) ?: return null
        return try {
            java.net.URLDecoder.decode(m.groupValues[1].replace("+", " "), "UTF-8")
        } catch (e: Exception) {
            null
        }
    }

    private fun base32ToHex(base32: String): String? {
        return try {
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
            var bits = 0L
            var bitCount = 0
            val out = java.io.ByteArrayOutputStream()
            for (ch in base32.uppercase()) {
                val v = alphabet.indexOf(ch)
                if (v == -1) return null
                bits = (bits shl 5) or v.toLong()
                bitCount += 5
                if (bitCount >= 8) {
                    bitCount -= 8
                    out.write(((bits ushr bitCount) and 0xFF).toInt())
                }
            }
            val bytes = out.toByteArray()
            if (bytes.size >= 20) bytes.take(20).joinToString("") { "%02x".format(it) } else null
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "TorrentManager"
        private val HASH_40 = Regex("[0-9a-fA-F]{40}")
        private val HASH_32 = Regex("[A-Za-z2-7]{32}")
        private val BTIH_40 = Regex("btih:([0-9a-fA-F]{40})", RegexOption.IGNORE_CASE)
        private val BTIH_32 = Regex("btih:([A-Za-z2-7]{32})", RegexOption.IGNORE_CASE)
    }
}

data class TorrentStatus(
    val progress: Float,
    val downloadRate: Float,
    val uploadRate: Float,
    val numSeeders: Int,
    val numPeers: Int,
    val bufferProgress: Float = 0f,
    val statusMessage: String = "",
)

data class TorrentFileInfo(
    val index: Int,
    val name: String,
    val path: String,
    val size: Long,
    val isMedia: Boolean,
    val season: Int? = null,
    val episode: Int? = null,
)
