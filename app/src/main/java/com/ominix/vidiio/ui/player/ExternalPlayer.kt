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
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setPackage(VLC_PACKAGE)
            // VLC keys off the MIME type; without it a URL with no file extension - which
            // is most torrent and addon streams - opens as an unknown type.
            setDataAndTypeAndNormalize(Uri.parse(url), "video/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            title?.let { putExtra("title", it) }
            if (positionMs > 0) {
                // VLC reads the resume point as milliseconds, as a long.
                putExtra("from_start", false)
                putExtra("position", positionMs)
            } else {
                putExtra("from_start", true)
            }
            subtitleUrl?.let { putExtra("subtitles_location", it) }

            // Scraped sources often 403 without the Referer/User-Agent they were found
            // with. VLC accepts them as a string array of alternating key/value.
            headers?.takeIf { it.isNotEmpty() }?.let { map ->
                putExtra(
                    "http-headers",
                    map.flatMap { (k, v) -> listOf(k, v) }.toTypedArray()
                )
            }
        }

        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not hand playback to VLC", e)
            Toast.makeText(context, "Could not open VLC", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private const val TAG = "ExternalPlayer"
}
