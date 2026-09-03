package com.example.vidiio.download

import com.example.vidiio.data.model.DownloadTask
import com.frostwire.jlibtorrent.*
import com.frostwire.jlibtorrent.alerts.Alert
import com.frostwire.jlibtorrent.alerts.AlertType
import com.frostwire.jlibtorrent.alerts.AddTorrentAlert
import com.frostwire.jlibtorrent.alerts.MetadataReceivedAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TorrentDownloader(private val session: SessionManager) {

    suspend fun download(
        task: DownloadTask,
        destDir: File,
        onProgress: (progress: Float, downloaded: Long, total: Long) -> Unit,
        isCancelled: () -> Boolean
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val s = session
            if (!s.isRunning) s.start()

            if (!destDir.exists()) destDir.mkdirs()

            val infoHash = parseInfoHash(task.url)
            var h: TorrentHandle? = if (infoHash != null) s.find(Sha1Hash(infoHash)) else null
            
            if (h == null) {
                val addLatch = CountDownLatch(1)
                var torrentHandle: TorrentHandle? = null

                val addListener = object : AlertListener {
                    override fun types(): IntArray = intArrayOf(AlertType.ADD_TORRENT.swig())
                    override fun alert(alert: Alert<*>) {
                        if (alert is AddTorrentAlert) {
                            if (infoHash == null || alert.handle().infoHash().toString() == infoHash) {
                                torrentHandle = alert.handle()
                                addLatch.countDown()
                            }
                        }
                    }
                }

                s.addListener(addListener)
                s.download(task.url, destDir)

                val added = addLatch.await(20, TimeUnit.SECONDS)
                s.removeListener(addListener)

                if (!added || torrentHandle == null) {
                    return@withContext DownloadResult.Error("Failed to add torrent")
                }
                h = torrentHandle!!
            } else {
                // Already in session, move storage if needed
                if (h.savePath() != destDir.absolutePath) {
                    h.moveStorage(destDir.absolutePath)
                }
                h.resume()
            }

            // Wait for metadata
            if (h.torrentFile() == null && !h.status().hasMetadata()) {
                val metadataLatch = CountDownLatch(1)
                val metadataListener = object : AlertListener {
                    override fun types(): IntArray = intArrayOf(AlertType.METADATA_RECEIVED.swig())
                    override fun alert(alert: Alert<*>) {
                        if (alert is MetadataReceivedAlert) {
                            val alertHandle = alert.handle()
                            if (h.infoHash() == null || alertHandle.infoHash().toString().lowercase() == h.infoHash().toString().lowercase()) {
                                metadataLatch.countDown()
                            }
                        }
                    }
                }
                s.addListener(metadataListener)
                val received = metadataLatch.await(90, TimeUnit.SECONDS)
                s.removeListener(metadataListener)
                if (!received && h.torrentFile() == null && !h.status().hasMetadata()) {
                    return@withContext DownloadResult.Error("Metadata timeout")
                }
            }

            val ti = h.torrentFile()!!
            val totalSize = ti.totalSize()
            
            // Prioritize all files for full download
            h.prioritizeFiles(Array(ti.numFiles()) { Priority.NORMAL })

            while (!isCancelled()) {
                val status = h.status()
                val progress = status.progress() * 100
                val downloaded = status.totalDone()
                
                onProgress(progress, downloaded, totalSize)

                if (progress >= 100) break
                
                delay(1000)
            }

            if (isCancelled()) {
                h.pause()
                return@withContext DownloadResult.Cancelled
            }

            val filePath = File(destDir, ti.name()).absolutePath
            DownloadResult.Success(filePath, totalSize)

        } catch (e: Exception) {
            DownloadResult.Error(e.message ?: "Unknown error")
        }
    }

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
            // ignore
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
            // ignore
        }
        return null
    }

    fun remove(url: String) {
        val infoHash = parseInfoHash(url)
        if (infoHash != null) {
            val h = session.find(Sha1Hash(infoHash))
            if (h != null) {
                session.remove(h)
            }
        }
    }
}
