package com.ominix.vidiio.ui.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ominix.vidiio.MainActivity

/**
 * The window-level side effects the player needs: immersive system bars, keep-screen-on,
 * display-cutout mode, and the MainActivity wake/wifi locks.
 *
 * Extracted from PlayerScreen because these are Activity-window concerns with paired
 * setup/teardown, and burying them among the playback logic made it hard to see that
 * every one of them is restored on dispose.
 */
@Composable
fun PlayerWindowEffects(notchSafe: Boolean, showControls: Boolean) {
    val context = LocalContext.current
    val activity = context as? Activity

    // "Fill, keep camera clear": NEVER lets Android letterbox the window just enough to
    // clear the camera hole; otherwise SHORT_EDGES fills into it. Belt-and-braces, the
    // video surface is also inset by the cutout at the call site.
    DisposableEffect(notchSafe) {
        val window = activity?.window
        val original = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window?.attributes?.layoutInDisplayCutoutMode
        } else {
            null
        }
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (notchSafe) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
        onDispose {
            if (window != null && original != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = original }
            }
        }
    }

    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        (activity as? MainActivity)?.acquirePlayerLocks()

        onDispose {
            (activity as? MainActivity)?.releasePlayerLocks()
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // System bars follow the transport controls.
    LaunchedEffect(showControls) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (showControls) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/**
 * Volume and screen brightness for the vertical drag gestures, plus the HUD flags that go
 * with them.
 *
 * Deliberately not in PlayerViewModel: both are properties of the Activity window and the
 * device, not of the playback session, and neither should survive the screen.
 */
@Stable
class PlayerDeviceControls internal constructor(
    private val activity: Activity?,
    private val audioManager: AudioManager,
) {
    var volume by mutableFloatStateOf(1.0f)
        private set
    var brightness by mutableFloatStateOf(0.5f)
        private set
    var isMuted by mutableStateOf(false)
        private set
    var showVolumeHud by mutableStateOf(false)
        internal set
    var showBrightnessHud by mutableStateOf(false)
        internal set

    internal fun seed() {
        volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        // Seed brightness from the window override if set, else from the system setting.
        val windowBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
        brightness = if (windowBrightness in 0f..1f) {
            windowBrightness
        } else {
            runCatching {
                Settings.System.getInt(
                    activity?.contentResolver ?: return@runCatching 128,
                    Settings.System.SCREEN_BRIGHTNESS
                ) / 255f
            }.getOrDefault(0.5f)
        }
    }

    fun adjustVolume(deltaFraction: Float) {
        volume = (volume + deltaFraction).coerceIn(0f, 1f)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            kotlin.math.round(volume * maxVol).toInt(),
            0
        )
        isMuted = volume <= 0f
        showVolumeHud = true
        showBrightnessHud = false
    }

    fun adjustBrightness(deltaFraction: Float) {
        brightness = (brightness + deltaFraction).coerceIn(0.02f, 1f)
        activity?.window?.let { w ->
            w.attributes = w.attributes.apply { screenBrightness = brightness }
        }
        showBrightnessHud = true
        showVolumeHud = false
    }

    fun hideHuds() {
        showVolumeHud = false
        showBrightnessHud = false
    }
}

@Composable
fun rememberPlayerDeviceControls(): PlayerDeviceControls {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val controls = remember { PlayerDeviceControls(activity, audioManager) }
    LaunchedEffect(Unit) { controls.seed() }
    return controls
}
