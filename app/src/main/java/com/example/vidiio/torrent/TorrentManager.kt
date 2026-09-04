package com.example.vidiio.torrent

import android.content.Context
import android.util.Log
import com.frostwire.jlibtorrent.*
import com.frostwire.jlibtorrent.alerts.Alert
import com.frostwire.jlibtorrent.alerts.AlertType
import com.frostwire.jlibtorrent.alerts.AddTorrentAlert
import com.frostwire.jlibtorrent.alerts.MetadataReceivedAlert
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TorrentManager(private val context: Context) {
    private val session: SessionManager? by lazy {
        (context.applicationContext as com.example.vidiio.VidiioApplication).torrentEngine.getSession()
    }
    private var handle: TorrentHandle? = null
    private var selectedFile: File? = null
    private var selectedFileIndex: Int = -1
    var targetFileSize: Long = 0L
        private set
    private val titleParser = TorrentTitleParser()
    
    private val _status = MutableStateFlow<TorrentStatus?>(null)
    val status: StateFlow<TorrentStatus?> = _status

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private companion object {
        /** How many pieces past the current read position to keep prefetching while buffering. */
        const val PREFETCH_PIECES = 24
    }

    private val globalTrackers = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://tracker.moeking.me:6969/announce",
        "udp://tracker.bit2party.jp:6969/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://explodie.org:6969/announce",
        "udp://ipv4.tracker.harry.lu:80/announce",
        "udp://p4p.arenabg.com:1337/announce",
        "udp://tracker.tiny-vps.com:6969/announce",
        "udp://tracker.zerobytes.xyz:1337/announce",
        "udp://9.rarbg.com:2810/announce"
    )

    val isAvailable: Boolean
        get() = (context.applicationContext as com.example.vidiio.VidiioApplication).torrentEngine.isAvailable

    suspend fun getMetadata(magnetUrl: String): List<TorrentFileInfo>? = withContext(Dispatchers.IO) {
        val s = session ?: return@withContext null
        val enrichedMagnet = appendTrackers(magnetUrl)
        Log.d("TorrentManager", "Fetching metadata for: $enrichedMagnet")
        _status.value = TorrentStatus(0f, 0f, 0f, 0, 0, 0f, "Fetching Metadata...")
        
        val infoHash = parseInfoHash(enrichedMagnet)
        var torrentHandle: TorrentHandle? = if (infoHash != null) s.find(Sha1Hash(infoHash)) else null

        if (torrentHandle == null && infoHash != null) {
            try {
                torrentHandle = s.find(Sha1Hash(infoHash))
            } catch (e: Exception) {
                // ignore
            }
        }

        if (torrentHandle == null) {
            val saveDir = File(context.cacheDir, "torrents")
            if (!saveDir.exists()) saveDir.mkdirs()

            val addLatch = CountDownLatch(1)
            var addedHandle: TorrentHandle? = null

            val addListener = object : AlertListener {
                override fun types(): IntArray = intArrayOf(AlertType.ADD_TORRENT.swig())
                override fun alert(alert: Alert<*>) {
                    if (alert is AddTorrentAlert) {
                        if (infoHash == null || alert.handle().infoHash().toString().lowercase() == infoHash.lowercase()) {
                            addedHandle = alert.handle()
                            addLatch.countDown()
                        }
                    }
                }
            }

            s.addListener(addListener)
            s.download(enrichedMagnet, saveDir)

            val added = addLatch.await(15, TimeUnit.SECONDS)
            s.removeListener(addListener)

            if (addedHandle != null) {
                torrentHandle = addedHandle
            } else if (infoHash != null) {
                torrentHandle = s.find(Sha1Hash(infoHash))
            }

            if (torrentHandle == null) {
                Log.e("TorrentManager", "Failed to add torrent after 15s")
                _status.value = _status.value?.copy(statusMessage = "Failed to add torrent")
                return@withContext null
            }
        }

        val h = torrentHandle!!
        handle = h
        h.resume()
        h.forceReannounce()

        if (h.torrentFile() == null && !h.status().hasMetadata()) {
            Log.d("TorrentManager", "Metadata not available, waiting...")
            _status.value = _status.value?.copy(statusMessage = "Finding Peers for Metadata...")
            val metadataLatch = CountDownLatch(1)
            val metadataListener = object : AlertListener {
                override fun types(): IntArray = intArrayOf(AlertType.METADATA_RECEIVED.swig())
                override fun alert(alert: Alert<*>) {
                    if (alert is MetadataReceivedAlert) {
                        val alertHandle = alert.handle()
                        if (infoHash == null || alertHandle.infoHash().toString().lowercase() == infoHash.lowercase()) {
                            Log.d("TorrentManager", "Metadata received alert")
                            metadataLatch.countDown()
                        }
                    }
                }
            }
            s.addListener(metadataListener)
            
            val reannounceJob = scope.launch {
                while (isActive && h.torrentFile() == null && !h.status().hasMetadata()) {
                    delay(10000)
                    h.forceReannounce()
                }
            }
            
            val received = metadataLatch.await(90, TimeUnit.SECONDS)
            reannounceJob.cancel()
            s.removeListener(metadataListener)
            
            if (!received && h.torrentFile() == null && !h.status().hasMetadata()) {
                Log.e("TorrentManager", "Metadata timeout after 90s")
                _status.value = _status.value?.copy(statusMessage = "Metadata fetching timed out")
                return@withContext null
            }
        }

        val ti = h.torrentFile() ?: return@withContext null
        Log.d("TorrentManager", "Metadata loaded: ${ti.name()}")
        _status.value = _status.value?.copy(statusMessage = "Metadata Loaded")
        
        val files = ti.files()
        val fileList = mutableListOf<TorrentFileInfo>()
        
        for (i in 0 until files.numFiles()) {
            val name = files.fileName(i)
            val path = files.filePath(i)
            val parsed = titleParser.parse(name)
            fileList.add(TorrentFileInfo(
                index = i,
                name = name,
                path = path,
                size = files.fileSize(i),
                isMedia = isMediaFile(name),
                season = parsed.season,
                episode = parsed.episode
            ))
        }
        
        fileList
    }

    private fun isMediaFile(name: String): Boolean {
        val extensions = listOf(".mp4", ".mkv", ".avi", ".mov", ".flv", ".wmv", ".m4v")
        return extensions.any { name.endsWith(it, true) }
    }

    suspend fun startStreaming(fileIndex: Int = -1, season: Int? = null, episode: Int? = null): File? = withContext(Dispatchers.IO) {
        val h = handle ?: return@withContext null
        val ti = h.torrentFile() ?: return@withContext null
        val files = ti.files()
        
        val targetIndex = if (fileIndex != -1) fileIndex else {
            selectBestFile(ti, season, episode)
        }

        if (targetIndex == -1) {
            Log.e("TorrentManager", "No suitable media file found")
            return@withContext null
        }
        selectedFileIndex = targetIndex
        targetFileSize = files.fileSize(targetIndex)
        Log.d("TorrentManager", "Starting streaming for file: ${files.fileName(targetIndex)}")
        _status.value = _status.value?.copy(statusMessage = "Connecting to Peers...")

        val saveDir = File(context.cacheDir, "torrents")
        val relativePath = files.filePath(targetIndex)
        selectedFile = File(saveDir, relativePath)

        _status.value = _status.value?.copy(statusMessage = "Allocating Disk Space...")
        
        // Set all files to priority 0 (don't download)
        h.prioritizeFiles(Array(files.numFiles()) { Priority.IGNORE })
        // Set target file to priority 4 (normal)
        h.filePriority(targetIndex, Priority.NORMAL)

        // Enable sequential download
        h.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)

        // Prioritize first and last pieces of the file (important for many formats)
        val fileOffset = files.fileOffset(targetIndex)
        val fileSize = files.fileSize(targetIndex)
        val pieceSize = ti.pieceLength()
        
        val firstPiece = (fileOffset / pieceSize).toInt()
        val lastPiece = ((fileOffset + fileSize - 1) / pieceSize).toInt()

        h.piecePriority(firstPiece, Priority.SEVEN)
        h.piecePriority(lastPiece, Priority.SEVEN)
        h.setPieceDeadline(firstPiece, 1000)
        h.setPieceDeadline(lastPiece, 1000)

        startMonitoring()
        _status.value = _status.value?.copy(statusMessage = "Ready to Stream")
        Log.d("TorrentManager", "Stream initialized instantly, ready to play")
        selectedFile
    }


    fun setPriorityRange(startByte: Long, length: Long) {
        val h = handle ?: return
        val ti = h.torrentFile() ?: return
        val files = ti.files()
        if (selectedFileIndex == -1) return

        val fileOffset = files.fileOffset(selectedFileIndex)
        val pieceSize = ti.pieceLength()
        
        val firstPiece = ((fileOffset + startByte) / pieceSize).toInt()
        val lastPiece = ((fileOffset + startByte + length - 1) / pieceSize).toInt()
        
        // Prioritize pieces in the requested range to the highest level
        for (i in firstPiece..lastPiece) {
            if (i < ti.numPieces()) {
                h.piecePriority(i, Priority.SEVEN)
                h.setPieceDeadline(i, 500) // Immediate deadline
            }
        }
        
        // Also prioritize a look-ahead window (sliding window of next 20 pieces)
        for (i in 1..20) {
            val piece = lastPiece + i
            if (piece < ti.numPieces()) {
                h.piecePriority(piece, Priority.SIX)
                h.setPieceDeadline(piece, (i + 1) * 1000)
            }
        }
    }

    fun updateStreamPriority(startByte: Long) {
        setPriorityRange(startByte, 1024 * 1024) // Default 1MB window for updates
    }

    /** Piece index of the selected file that contains [fileByte], or null if not ready. */
    private fun pieceForFileByte(fileByte: Long): Int? {
        val ti = handle?.torrentFile() ?: return null
        if (selectedFileIndex == -1) return null
        val fileOffset = ti.files().fileOffset(selectedFileIndex)
        return ((fileOffset + fileByte) / ti.pieceLength()).toInt()
    }

    /**
     * Blocks until the piece containing [fileByte] is available on disk, re-asserting
     * high priority + short deadlines on that piece and a look-ahead window while waiting.
     * @return true once the data is available, false if it timed out.
     */
    fun awaitBytes(fileByte: Long, timeoutMs: Long = 120_000L): Boolean {
        val h = handle ?: return false
        val ti = h.torrentFile() ?: return false
        val piece = pieceForFileByte(fileByte) ?: return false
        if (h.havePiece(piece)) return true

        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (h.havePiece(piece)) return true
            for (i in 0..PREFETCH_PIECES) {
                val p = piece + i
                if (p in 0 until ti.numPieces()) {
                    h.piecePriority(p, if (i == 0) Priority.SEVEN else Priority.SIX)
                    h.setPieceDeadline(p, 200 + i * 400)
                }
            }
            _status.value = _status.value?.copy(statusMessage = "Buffering...")
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
                return h.havePiece(piece)
            }
        }
        return h.havePiece(piece)
    }

    /**
     * Largest byte offset (file-relative, exclusive) that is contiguously present on disk
     * starting at [fileByte]. Returns [fileByte] itself when that position is not downloaded.
     */
    fun contiguousAvailableEnd(fileByte: Long): Long {
        val h = handle ?: return fileByte
        val ti = h.torrentFile() ?: return fileByte
        if (selectedFileIndex == -1) return fileByte
        val fileOffset = ti.files().fileOffset(selectedFileIndex)
        val pieceSize = ti.pieceLength().toLong()

        var piece = ((fileOffset + fileByte) / pieceSize).toInt()
        if (piece !in 0 until ti.numPieces() || !h.havePiece(piece)) return fileByte

        var scanned = 0
        while (piece + 1 < ti.numPieces() && h.havePiece(piece + 1) && scanned < 512) {
            piece++
            scanned++
        }
        val endAbs = (piece + 1).toLong() * pieceSize
        val fileEndAbs = fileOffset + (if (targetFileSize > 0) targetFileSize else Long.MAX_VALUE / 2)
        return minOf(endAbs, fileEndAbs) - fileOffset
    }

    private fun selectBestFile(ti: TorrentInfo, season: Int? = null, episode: Int? = null): Int {
        val files = ti.files()
        val mediaFiles = mutableListOf<Int>()
        for (i in 0 until files.numFiles()) {
            if (isMediaFile(files.fileName(i))) {
                mediaFiles.add(i)
            }
        }

        if (mediaFiles.isEmpty()) return -1

        // If season/episode provided, try to match
        if (season != null && episode != null) {
            for (index in mediaFiles) {
                val parsed = titleParser.parse(files.fileName(index))
                if (parsed.season == season && parsed.episode == episode) {
                    return index
                }
            }
        } else if (episode != null) {
            // Anime often has only episode number
            for (index in mediaFiles) {
                val parsed = titleParser.parse(files.fileName(index))
                if (parsed.episode == episode) {
                    return index
                }
            }
        }

        // Fallback: largest media file
        var maxSize = -1L
        var largestIndex = -1
        for (index in mediaFiles) {
            if (files.fileSize(index) > maxSize) {
                maxSize = files.fileSize(index)
                largestIndex = index
            }
        }
        return largestIndex
    }

    private fun startMonitoring() {
        scope.launch {
            while (isActive) {
                handle?.let { h ->
                    val s = h.status()
                    
                    // Calculate buffer progress based on target file pieces
                    val bufferProgress = calculateBufferProgress(h)

                    _status.value = TorrentStatus(
                        progress = s.progress() * 100,
                        downloadRate = s.downloadPayloadRate() / 1024f, // KB/s
                        uploadRate = s.uploadPayloadRate() / 1024f, // KB/s
                        numSeeders = s.numSeeds(),
                        numPeers = s.numPeers(),
                        bufferProgress = bufferProgress,
                        statusMessage = _status.value?.statusMessage ?: ""
                    )
                }
                delay(1000)
            }
        }
    }




    private fun calculateBufferProgress(h: TorrentHandle): Float {
        val ti = h.torrentFile() ?: return 0f
        if (selectedFileIndex == -1) return 0f
        
        val fileStorage = ti.files()
        val pieceSize = ti.pieceLength()
        val fileOffset = fileStorage.fileOffset(selectedFileIndex)
        val fileSize = fileStorage.fileSize(selectedFileIndex)
        
        val firstPiece = (fileOffset / pieceSize).toInt()
        val lastPiece = ((fileOffset + fileSize - 1) / pieceSize).toInt()
        
        var piecesHave = 0
        val totalPieces = lastPiece - firstPiece + 1
        for (i in firstPiece..lastPiece) {
            if (h.havePiece(i)) piecesHave++
        }
        
        return (piecesHave.toFloat() / totalPieces) * 100
    }

    private fun appendTrackers(magnetUrl: String): String {
        return try {
            var url = magnetUrl
            globalTrackers.forEach { tracker ->
                if (!url.contains(tracker)) {
                    url += "&tr=${java.net.URLEncoder.encode(tracker, "UTF-8")}"
                }
            }
            url
        } catch (e: Exception) {
            magnetUrl
        }
    }

    fun stop() {
        scope.cancel()
        // Do not stop shared session here
    }

    fun getSelectedFile(): File? = selectedFile

    private fun parseInfoHash(url: String): String? {
        try {
            val regex = "xt=urn:btih:([a-zA-Z0-9]+)".toRegex()
            val match = regex.find(url)
            if (match != null) {
                val hash = match.groupValues[1].lowercase()
                if (hash.length == 40) {
                    return hash
                } else if (hash.length == 32) {
                    return base32ToHex(hash)
                }
            }
        } catch (e: Exception) {
            Log.e("TorrentManager", "Failed to parse info hash: ${e.message}")
        }
        return null
    }

    private fun base32ToHex(base32: String): String? {
        try {
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
            var bits = 0L
            var bitCount = 0
            val out = java.io.ByteArrayOutputStream()
            for (ch in base32.uppercase()) {
                val val_ = alphabet.indexOf(ch)
                if (val_ == -1) return null
                bits = (bits shl 5) or val_.toLong()
                bitCount += 5
                if (bitCount >= 8) {
                    bitCount -= 8
                    out.write(((bits ushr bitCount) and 0xFF).toInt())
                }
            }
            val bytes = out.toByteArray()
            if (bytes.size >= 20) {
                return bytes.take(20).joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            Log.e("TorrentManager", "Base32 decode error", e)
        }
        return null
    }
}

data class TorrentStatus(
    val progress: Float,
    val downloadRate: Float,
    val uploadRate: Float,
    val numSeeders: Int,
    val numPeers: Int,
    val bufferProgress: Float = 0f,
    val statusMessage: String = ""
)

data class TorrentFileInfo(
    val index: Int,
    val name: String,
    val path: String,
    val size: Long,
    val isMedia: Boolean,
    val season: Int? = null,
    val episode: Int? = null
)

