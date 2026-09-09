package com.ominix.vidiio.download

import android.content.Context
import android.util.Log
import com.ominix.vidiio.data.model.DownloadTask
import com.ominix.vidiio.torrent.MediaFileSelector
import com.ominix.vidiio.torrent.TsTorrent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Downloads a torrent by streaming it out of the embedded TorrServer engine and writing
 * that HTTP stream to disk with [HttpDownloader] - the same engine used for playback,
 * so there is no second (crashy) native BitTorrent library.
 */
class TorrentDownloader(
    private val context: Context,
    private val httpDownloader: HttpDownloader,
) {
    private val engine get() =
        (context.applicationContext as com.ominix.vidiio.VidiioApplication).torrServerEngine

    suspend fun download(
        task: DownloadTask,
        destDir: File,
        onProgress: (progress: Float, downloaded: Long, total: Long) -> Unit,
        isCancelled: () -> Boolean
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            if (!engine.ensureStarted()) {
                return@withContext DownloadResult.Error("Torrent engine failed to start")
            }
            val api = engine.getApi()
                ?: return@withContext DownloadResult.Error("Torrent engine unavailable")

            val added = api.addTorrent(task.url, task.title)
                ?: return@withContext DownloadResult.Error("Failed to add torrent")
            val hash = added.hash.ifEmpty { null }
                ?: return@withContext DownloadResult.Error("Invalid magnet link")

            // Wait for metadata.
            var info: TsTorrent? = added
            val deadline = System.currentTimeMillis() + 60_000
            while (System.currentTimeMillis() < deadline) {
                if (isCancelled()) { api.dropTorrent(hash); return@withContext DownloadResult.Cancelled }
                info = runCatching { api.getTorrent(hash) }.getOrNull()
                if (info != null && info.fileStats.isNotEmpty()) break
                delay(500)
            }
            val files = info?.fileStats.orEmpty()
            if (files.isEmpty()) {
                api.dropTorrent(hash)
                return@withContext DownloadResult.Error("Metadata timed out")
            }

            // Prefer the exact file the Stremio addon chose (by name, then index),
            // falling back to the media-file heuristic.
            val wantedName = task.torrentFileName?.substringAfterLast('/')?.trim()
            val file = files.firstOrNull { fs ->
                    wantedName != null && (
                        fs.path.substringAfterLast('/').equals(wantedName, ignoreCase = true) ||
                        fs.path.endsWith(wantedName, ignoreCase = true)
                    )
                }
                ?: task.torrentFileIndex?.let { idx -> files.getOrNull(idx) ?: files.firstOrNull { it.id - 1 == idx } }
                ?: MediaFileSelector.select(files, null, null, null)
                ?: return@withContext DownloadResult.Error("No media file in torrent").also { api.dropTorrent(hash) }

            val streamUrl = api.streamUrl(hash, file.id, file.path)
            Log.d(TAG, "Downloading torrent file '${file.path}' via $streamUrl")

            // Hand the TorrServer stream to the plain HTTP downloader.
            val httpTask = task.copy(
                url = streamUrl,
                title = file.path.substringAfterLast('/').ifBlank { task.title },
            )
            val result = httpDownloader.download(httpTask, destDir, onProgress, isCancelled)
            api.dropTorrent(hash)
            result
        } catch (e: Exception) {
            DownloadResult.Error(e.message ?: "Unknown error")
        }
    }

    companion object {
        private const val TAG = "TorrentDownloader"
    }
}
