package com.example.vidiio

import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.vidiio.data.repository.AppTheme
import com.example.vidiio.data.repository.ColorTheme
import com.example.vidiio.ui.VidiioApp
import com.example.vidiio.ui.theme.VidiioTheme

class MainActivity : ComponentActivity() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsRepository = (applicationContext as VidiioApplication).settingsRepository

        initLocks()

        enableEdgeToEdge()
        setContent {
            val theme by settingsRepository.themeFlow.collectAsState(initial = AppTheme.SYSTEM)
            val colorTheme by settingsRepository.colorThemeFlow.collectAsState(initial = ColorTheme.RED)
            val dynamicColor by settingsRepository.dynamicColorFlow.collectAsState(initial = true)

            val darkTheme = when (theme) {
                AppTheme.DARK -> true
                AppTheme.LIGHT -> false
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }

            VidiioTheme(
                darkTheme = darkTheme,
                colorTheme = colorTheme,
                dynamicColor = dynamicColor
            ) {
                VidiioApp()
            }
        }
    }

    private fun initLocks() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Vidiio:WakeLock")

        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        wifiLock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "Vidiio:WifiLock")
        } else {
            @Suppress("DEPRECATION")
            wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Vidiio:WifiLock")
        }
    }

    fun acquirePlayerLocks() {
        if (wakeLock?.isHeld == false) wakeLock?.acquire(2 * 60 * 60 * 1000L) // 2 hours
        if (wifiLock?.isHeld == false) wifiLock?.acquire()
    }

    fun releasePlayerLocks() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (wifiLock?.isHeld == true) wifiLock?.release()
    }

    override fun onResume() {
        super.onResume()
        // Use a timeout of 10 minutes as a safety measure
        wakeLock?.acquire(10 * 60 * 1000L)
        wifiLock?.acquire()
    }

    override fun onPause() {
        super.onPause()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (wifiLock?.isHeld == true) wifiLock?.release()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (wifiLock?.isHeld == true) wifiLock?.release()
    }
}
