package com.example.vidiio.torrent

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.os.Build

class TorrentService : Service() {
    private val binder = LocalBinder()
    lateinit var torrentManager: TorrentManager
        private set
    private var streamServer: TorrentStreamServer? = null
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    inner class LocalBinder : Binder() {
        fun getService(): TorrentService = this@TorrentService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        torrentManager = TorrentManager(this)
        createNotificationChannel()
    }

    fun getMetadata(magnetUrl: String, onMetadata: (List<TorrentFileInfo>?) -> Unit) {
        Log.d("TorrentService", "getMetadata requested for: $magnetUrl")
        scope.launch {
            val fileList = torrentManager.getMetadata(magnetUrl)
            Log.d("TorrentService", "getMetadata result: ${fileList?.size ?: "null"} files found")
            onMetadata(fileList)
        }
    }

    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

    private fun acquireWifiLock() {
        if (wifiLock == null) {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager
            wifiLock = wifiManager.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "VidiioWifiLock")
            wifiLock?.setReferenceCounted(false)
        }
        wifiLock?.acquire()
    }

    private fun releaseWifiLock() {
        try {
            wifiLock?.release()
        } catch (e: Exception) {}
    }

    fun startStreaming(fileIndex: Int = -1, season: Int? = null, episode: Int? = null, onReady: (String) -> Unit) {
        Log.d("TorrentService", "startStreaming requested: fileIndex=$fileIndex, S=$season, E=$episode")
        if (!torrentManager.isAvailable) {
            Log.e("TorrentService", "Torrent engine not available")
            onReady("")
            return
        }
        acquireWifiLock()
        scope.launch {
            val file = torrentManager.startStreaming(fileIndex, season, episode)
            if (file != null) {
                Log.d("TorrentService", "File ready for streaming: ${file.absolutePath}")
                streamServer?.stop()
                streamServer = TorrentStreamServer(file, torrentManager)
                try {
                    streamServer?.start()
                    val url = "http://127.0.0.1:8888/${file.name}"
                    Log.d("TorrentService", "Stream server started at: $url")
                    onReady(url)
                    startForeground(1, createNotification("Streaming torrent...", file.name))
                } catch (e: Exception) {
                    Log.e("TorrentService", "Failed to start stream server", e)
                    releaseWifiLock()
                    onReady("")
                }
            } else {
                Log.e("TorrentService", "Failed to get file from torrentManager")
                releaseWifiLock()
                onReady("")
            }
        }
    }



    fun stopStreaming() {
        releaseWifiLock()
        streamServer?.stop()
        torrentManager.stop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWifiLock()
        streamServer?.stop()
        torrentManager.stop()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "torrent_channel",
                "Torrent Streaming",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, text: String): Notification {
        return NotificationCompat.Builder(this, "torrent_channel")
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .build()
    }
}
