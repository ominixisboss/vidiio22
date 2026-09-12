package com.ominix.vidiio.ui.player

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast

/**
 * Handing the current stream to VLC.
 *
 * VLC is worth offering because it plays codecs and containers ExoPlayer will not - DTS
 * and TrueHD audio, some MKV variants, odd subtitle muxes - which is exactly the material
 * that turns up in torrent releases.
 *
 * Everything here is best-effort: VLC's intent extras are a de-facto interface, not a
 * supported API, so an extra it no longer honours must degrade to "plays from the start"
 * rather than failing to launch.
 */
object ExternalPlayer {

    const val VLC_PACKAGE = "org.videolan.vlc"

    /**
     * Whether VLC is installed.
     *
     * Needs the `<queries>` entry in the manifest: from Android 11 a package this app has
     * not declared is invisible to it, and this silently returns false without one.
     */
    fun isVlcInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(VLC_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Opens Google Play Store to install VLC. */
    fun openVlcInPlayStore(context: Context) {
        val marketUri = Uri.parse("market://details?id=$VLC_PACKAGE")
        val marketIntent = Intent(Intent.ACTION_VIEW, marketUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(marketIntent)
        } catch (e: Exception) {
            val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$VLC_PACKAGE")
            val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(webIntent)
            } catch (ex: Exception) {
                Log.w(TAG, "Could not open Play Store for VLC", ex)
                Toast.makeText(context, "Could not open Play Store", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Copies stream URL to system clipboard for external use. */
    fun copyStreamUrlToClipboard(context: Context, url: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Vidiio Stream URL", url)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "Stream URL copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    /**
     * Opens [url] in VLC, resuming at [positionMs] and side-loading [subtitleUrl] if given.
     *
     * @return true if VLC was launched. False means it is not installed or refused the
     *   intent, and the caller should keep playing internally.
     */
    fun playInVlc(
        context: Context,
        url: String,
        title: String?,
        positionMs: Long = 0L,
        subtitleUrl: String? = null,
        headers: Map<String, String>? = null,
    ): Boolean {
        if (!isVlcInstalled(context)) {
            Log.w(TAG, "VLC is not installed on this device")
            Toast.makeText(context, "VLC Player is not installed. Opening Play Store...", Toast.LENGTH_LONG).show()
            openVlcInPlayStore(context)
            return false
        }

        val uri = if (url.startsWith("/")) {
            Uri.fromFile(java.io.File(url))
        } else {
            Uri.parse(url)
        }

        val effectiveHeaders = buildMap {
            put(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            )
            headers?.let { putAll(it) }
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setPackage(VLC_PACKAGE)
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            title?.takeIf { it.isNotBlank() }?.let { putExtra("title", it) }
            putExtra("sticky", true)

            if (positionMs > 0) {
                putExtra("from_start", false)
                putExtra("position", positionMs)
            } else {
                putExtra("from_start", true)
            }

            subtitleUrl?.takeIf { it.isNotBlank() }?.let {
                putExtra("subtitles_location", it)
                putExtra("subtitles_encoding", "UTF-8")
            }

            putExtra(
                "http-headers",
                effectiveHeaders.flatMap { (k, v) -> listOf(k, v) }.toTypedArray()
            )
        }

        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Primary VLC intent failed, trying fallback", e)
            val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                setPackage(VLC_PACKAGE)
                setData(uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                title?.takeIf { it.isNotBlank() }?.let { putExtra("title", it) }
                if (positionMs > 0) {
                    putExtra("from_start", false)
                    putExtra("position", positionMs)
                }
            }
            try {
                context.startActivity(fallbackIntent)
                true
            } catch (ex: Exception) {
                Log.e(TAG, "Could not open VLC", ex)
                Toast.makeText(context, "Could not open VLC Player (${ex.localizedMessage})", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }

    private const val TAG = "ExternalPlayer"
}
