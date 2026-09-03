package com.example.vidiio.download

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.vidiio.VidiioApplication
import com.example.vidiio.data.model.DownloadStatus
import com.example.vidiio.data.model.DownloadTask
import com.example.vidiio.data.model.DownloadType
import com.example.vidiio.data.repository.DownloadRepository
import com.frostwire.jlibtorrent.SessionManager
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class DownloadService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val MAX_CONCURRENT_DOWNLOADS = 2
    
    private lateinit var repository: DownloadRepository
    private lateinit var torrentDownloader: TorrentDownloader
    private lateinit var notificationManager: NotificationManager
    private lateinit var torrentSession: SessionManager

    companion object {
        private const val CHANNEL_ID = "downloads_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_REMOVE = "com.example.vidiio.download.ACTION_REMOVE"
        const val EXTRA_URL = "extra_url"
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as VidiioApplication
        repository = app.downloadRepository
        torrentSession = app.torrentEngine.getSession() ?: SessionManager()
        torrentDownloader = TorrentDownloader(torrentSession)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REMOVE) {
            val url = intent.getStringExtra(EXTRA_URL)
            if (url != null) {
                torrentDownloader.remove(url)
            }
        }
        
        startForeground(NOTIFICATION_ID, createNotification("Starting downloads...", 0f))
        processQueue()
        return START_STICKY
    }

    private fun processQueue() {
        scope.launch {
            val queuedTasks = repository.getActiveDownloads()
            
            // Cancel removed or paused jobs
            val taskIds = queuedTasks.map { it.id }.toSet()
            activeJobs.keys.forEach { id ->
                if (!taskIds.contains(id)) {
                    activeJobs[id]?.cancel()
                    activeJobs.remove(id)
                }
            }

            for (task in queuedTasks) {
                if (activeJobs.size >= MAX_CONCURRENT_DOWNLOADS) break
                
                if (task.status == DownloadStatus.QUEUED && !activeJobs.containsKey(task.id)) {
                    startDownload(task)
                }
            }
            
            if (activeJobs.isEmpty()) {
                // If there are torrents still active in the session (e.g. streaming), don't stop service?
                // Actually, this service is for downloads.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    stopForeground(true)
                }
                stopSelf()
            }
        }
    }

    private fun startDownload(task: DownloadTask) {
        val job = scope.launch {
            repository.updateDownload(task.copy(status = DownloadStatus.DOWNLOADING))
            
            val destDir = getDownloadDir()
            val result = torrentDownloader.download(
                task, destDir,
                onProgress = { progress, downloaded, total ->
                    scope.launch {
                        updateProgress(task.id, task.title, progress, downloaded)
                    }
                },
                isCancelled = { !isActive }
            )

            handleResult(task.id, result)
            activeJobs.remove(task.id)
            processQueue()
        }
        activeJobs[task.id] = job
    }

    private fun getDownloadDir(): File {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Vidiio")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private suspend fun updateProgress(id: Long, title: String, progress: Float, downloaded: Long) {
        repository.updateProgress(id, DownloadStatus.DOWNLOADING, progress, downloaded)
        notificationManager.notify(NOTIFICATION_ID, createNotification("Downloading $title", progress))
    }

    private suspend fun handleResult(id: Long, result: DownloadResult) {
        val task = repository.getDownloadById(id) ?: return
        when (result) {
            is DownloadResult.Success -> {
                repository.updateDownload(task.copy(
                    status = DownloadStatus.COMPLETED,
                    progress = 100f,
                    filePath = result.filePath,
                    totalSize = result.totalSize,
                    downloadedSize = result.totalSize
                ))
            }
            is DownloadResult.Error -> {
                repository.updateDownload(task.copy(
                    status = DownloadStatus.FAILED,
                    errorMessage = result.message
                ))
            }
            DownloadResult.Cancelled -> {
                // Keep status as PAUSED or CANCELLED as set by the manager
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        // Do NOT stop the shared session here
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String, progress: Float): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vidiio Downloader")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (progress > 0) {
            builder.setProgress(100, progress.toInt(), false)
        }

        return builder.build()
    }
}
