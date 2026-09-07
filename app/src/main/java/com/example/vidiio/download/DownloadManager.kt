package com.example.vidiio.download

import android.content.Context
import android.content.Intent
import com.example.vidiio.data.model.DownloadStatus
import com.example.vidiio.data.model.DownloadTask
import com.example.vidiio.data.model.DownloadType
import com.example.vidiio.data.repository.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class DownloadManager(
    private val context: Context,
    private val repository: DownloadRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun enqueue(title: String, url: String, headers: Map<String, String>? = null) {
        val isMagnet = url.startsWith("magnet:")
        // Progressive HTTP files only. Embed pages and HLS playlists aren't a single file.
        val isPlainHttp = url.startsWith("http") &&
            !url.contains(".m3u8") &&
            !url.contains("embed") &&
            !url.contains("vidsrc")
        if (!isMagnet && !isPlainHttp) return

        scope.launch {
            val task = DownloadTask(
                title = title,
                url = url,
                type = if (isMagnet) DownloadType.TORRENT else DownloadType.HTTP,
                status = DownloadStatus.QUEUED,
                headersJson = headers?.takeIf { it.isNotEmpty() }?.let { org.json.JSONObject(it).toString() }
            )
            repository.insertDownload(task)
            startService()
        }
    }

    fun pause(id: Long) {
        scope.launch {
            val task = repository.getDownloadById(id)
            task?.let {
                repository.updateDownload(it.copy(status = DownloadStatus.PAUSED))
                // The service will pick up the change or we can notify it
                startService() 
            }
        }
    }

    fun resume(id: Long) {
        scope.launch {
            val task = repository.getDownloadById(id)
            task?.let {
                repository.updateDownload(it.copy(status = DownloadStatus.QUEUED))
                startService()
            }
        }
    }

    fun cancel(id: Long) {
        scope.launch {
            val task = repository.getDownloadById(id)
            task?.let {
                // For "Cancel", we stop the download and remove it from DB/disk to "clear" it
                delete(id)
            }
        }
    }

    fun delete(id: Long) {
        scope.launch {
            val task = repository.getDownloadById(id)
            task?.let {
                // 1. Delete from database immediately to update UI
                repository.deleteDownload(it)

                // 2. If it was active, notify service to stop downloading/writing
                if (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED) {
                    // Since it's gone from DB, startService() will trigger processQueue()
                    // which will see it's missing and cancel the active job.
                    startService()
                    
                    // If it's a torrent, tell service to remove it from session explicitly
                    if (it.type == DownloadType.TORRENT) {
                        val intent = Intent(context, DownloadService::class.java).apply {
                            action = DownloadService.ACTION_REMOVE
                            putExtra(DownloadService.EXTRA_URL, it.url)
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                    }
                    
                    // Give service a moment to stop before deleting file
                    delay(300)
                }

                // 3. Delete physical file
                val fileToDelete = if (it.filePath != null) {
                    File(it.filePath)
                } else {
                    null
                }

                fileToDelete?.let { file ->
                    try {
                        if (file.exists()) {
                            if (file.isDirectory) {
                                file.deleteRecursively()
                            } else {
                                file.delete()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                // Final notify to service
                startService()
            }
        }
    }

    private fun startService() {
        val intent = Intent(context, DownloadService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
