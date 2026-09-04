package com.example.vidiio.torrent

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * Local HTTP server that streams a single file out of a torrent to ExoPlayer.
 *
 * The key detail: the file on disk is sparse (libtorrent allocates it at full size but
 * only fills in pieces as they arrive). Serving it with a plain FileInputStream hands the
 * player zeros for regions that haven't downloaded yet, which ExoPlayer reports as a
 * playback error. Instead every response body is a [TorrentInputStream] that blocks until
 * the piece under the current read position is verified on disk, while nudging libtorrent
 * to fetch that piece (and a look-ahead window) first.
 */
class TorrentStreamServer(
    private val file: File,
    private val torrentManager: TorrentManager,
    port: Int = 8888
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        // Wait briefly for libtorrent to create the file entry.
        var attempts = 0
        while (!file.exists() && attempts < 50) {
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
                break
            }
            attempts++
        }
        if (!file.exists()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found")
        }

        return try {
            val mimeType = resolveMimeType(file.name)
            val fileLength = if (torrentManager.targetFileSize > 0) torrentManager.targetFileSize else file.length()
            val isHead = session.method == Method.HEAD

            val rangeHeader = session.headers["range"]
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                val (start, end) = parseRange(rangeHeader.substring(6).trim(), fileLength)

                if (start < 0 || start >= fileLength) {
                    val res = newFixedLengthResponse(
                        Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, ""
                    )
                    res.addHeader("Content-Range", "bytes */$fileLength")
                    return res
                }

                val dataLen = end - start + 1
                torrentManager.setPriorityRange(start, dataLen)
                Log.d("TorrentStreamServer", "Range $start-$end/$fileLength (${if (isHead) "HEAD" else "GET"})")

                val body: InputStream =
                    if (isHead) emptyStream() else TorrentInputStream(file, torrentManager, start, end)
                val res = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, body, dataLen)
                res.addHeader("Accept-Ranges", "bytes")
                res.addHeader("Content-Range", "bytes $start-$end/$fileLength")
                res
            } else {
                torrentManager.setPriorityRange(0, 2 * 1024 * 1024L)
                val body: InputStream =
                    if (isHead) emptyStream() else TorrentInputStream(file, torrentManager, 0, fileLength - 1)
                val res = newFixedLengthResponse(Response.Status.OK, mimeType, body, fileLength)
                res.addHeader("Accept-Ranges", "bytes")
                res
            }
        } catch (e: Exception) {
            Log.e("TorrentStreamServer", "serve() failed", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error: ${e.message}")
        }
    }

    /** Parses a single HTTP byte range, returning an inclusive [start, end] clamped to [totalLength]. */
    private fun parseRange(value: String, totalLength: Long): Pair<Long, Long> {
        val dash = value.indexOf('-')
        if (dash == -1) return -1L to -1L

        val startStr = value.substring(0, dash).trim()
        val endStr = value.substring(dash + 1).trim()

        return try {
            when {
                startStr.isEmpty() && endStr.isNotEmpty() -> {
                    // Suffix range: last N bytes.
                    val n = endStr.toLong().coerceAtMost(totalLength)
                    (totalLength - n) to (totalLength - 1)
                }
                startStr.isNotEmpty() -> {
                    val start = startStr.toLong()
                    val end = if (endStr.isNotEmpty()) endStr.toLong() else totalLength - 1
                    start to end.coerceAtMost(totalLength - 1)
                }
                else -> -1L to -1L
            }
        } catch (e: NumberFormatException) {
            -1L to -1L
        }
    }

    private fun emptyStream() = object : InputStream() {
        override fun read(): Int = -1
    }

    private fun resolveMimeType(fileName: String): String = when {
        fileName.endsWith(".mp4", true) -> "video/mp4"
        fileName.endsWith(".mkv", true) -> "video/x-matroska"
        fileName.endsWith(".webm", true) -> "video/webm"
        fileName.endsWith(".avi", true) -> "video/x-msvideo"
        fileName.endsWith(".mov", true) -> "video/quicktime"
        fileName.endsWith(".m4v", true) -> "video/x-m4v"
        fileName.endsWith(".ts", true) -> "video/mp2t"
        else -> "video/mp4"
    }

    /**
     * InputStream over a sparse torrent file that blocks each read until the underlying
     * piece is downloaded. Never returns 0 for a non-empty buffer (NanoHTTPD treats that
     * as end-of-stream); it either returns >=1 byte, returns -1 at EOF, or throws on timeout.
     */
    private class TorrentInputStream(
        file: File,
        private val torrentManager: TorrentManager,
        startByte: Long,
        private val lastByteInclusive: Long
    ) : InputStream() {

        private val raf = RandomAccessFile(file, "r")
        private var pos = startByte

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (pos > lastByteInclusive) return -1

            while (true) {
                if (!torrentManager.awaitBytes(pos)) {
                    throw IOException("Timed out waiting for torrent data at byte $pos")
                }

                val chunkEnd = minOf(torrentManager.contiguousAvailableEnd(pos), lastByteInclusive + 1)
                val toRead = minOf(len.toLong(), chunkEnd - pos).toInt()
                if (toRead <= 0) {
                    // Piece flagged present but not flushed to disk yet - back off and retry.
                    sleep(50)
                    continue
                }

                raf.seek(pos)
                val n = raf.read(b, off, toRead)
                if (n <= 0) {
                    sleep(50)
                    continue
                }
                pos += n
                return n
            }
        }

        override fun available(): Int {
            val end = minOf(torrentManager.contiguousAvailableEnd(pos), lastByteInclusive + 1)
            return (end - pos).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        }

        override fun close() {
            try {
                raf.close()
            } catch (e: Exception) {
                // ignore
            }
        }

        private fun sleep(ms: Long) {
            try {
                Thread.sleep(ms)
            } catch (e: InterruptedException) {
                throw IOException("Interrupted while streaming", e)
            }
        }
    }
}
