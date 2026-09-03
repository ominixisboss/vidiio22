package com.example.vidiio.torrent

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class TorrentStreamServer(
    private val file: File,
    private val torrentManager: TorrentManager,
    port: Int = 8888
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        var attempts = 0
        while (!file.exists() && attempts < 20) {
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
                break
            }
            attempts++
        }

        if (!file.exists()) {
            try {
                file.parentFile?.mkdirs()
                file.createNewFile()
            } catch (e: Exception) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found")
            }
        }

        return try {
            val header = session.headers
            val mimeType = resolveMimeType(file.name)
            val fileLength = if (torrentManager.targetFileSize > 0) torrentManager.targetFileSize else file.length()
            
            val rangeHeader = header["range"]
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                var start: Long = 0
                var end: Long = -1
                
                val rangeValue = rangeHeader.substring(6).trim()
                val dashIndex = rangeValue.indexOf('-')
                
                if (dashIndex != -1) {
                    val startStr = rangeValue.substring(0, dashIndex)
                    val endStr = rangeValue.substring(dashIndex + 1)
                    
                    try {
                        if (startStr.isNotEmpty()) start = startStr.toLong()
                        if (endStr.isNotEmpty()) end = endStr.toLong()
                    } catch (e: NumberFormatException) {}
                }

                if (end == -1L || end >= fileLength) {
                    end = fileLength - 1
                }

                if (start >= fileLength) {
                    val res = newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE,
                        MIME_PLAINTEXT, "")
                    res.addHeader("Content-Range", "bytes */$fileLength")
                    res
                } else {
                    val dataLen = end - start + 1
                    
                    // Inform manager about exact byte range requested
                    torrentManager.setPriorityRange(start, dataLen)
                    
                    val fis = FileInputStream(file)
                    try {
                        fis.skip(start)
                    } catch (e: Exception) {}
                    
                    val res = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, fis, dataLen)
                    res.addHeader("Content-Length", dataLen.toString())
                    res.addHeader("Content-Range", "bytes $start-$end/$fileLength")
                    res.addHeader("Accept-Ranges", "bytes")
                    res
                }
            } else {
                torrentManager.setPriorityRange(0, 2 * 1024 * 1024L)
                
                val fis = FileInputStream(file)
                val res = newFixedLengthResponse(Response.Status.OK, mimeType, fis, fileLength)
                res.addHeader("Content-Length", fileLength.toString())
                res.addHeader("Accept-Ranges", "bytes")
                res
            }
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error: ${e.message}")
        }
    }


    private fun resolveMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".mp4", true) -> "video/mp4"
            fileName.endsWith(".mkv", true) -> "video/x-matroska"
            fileName.endsWith(".avi", true) -> "video/x-msvideo"
            fileName.endsWith(".mov", true) -> "video/quicktime"
            else -> "video/mp4"
        }
    }
}
