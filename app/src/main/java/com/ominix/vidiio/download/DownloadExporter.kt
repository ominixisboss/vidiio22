package com.ominix.vidiio.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.ominix.vidiio.data.model.DownloadTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.util.Log
import kotlinx.coroutines.CancellationException

sealed class ExportResult {
    data class Success(val location: String) : ExportResult()
    data class Error(val message: String) : ExportResult()
    /** Legacy devices (API < 29) that still need WRITE_EXTERNAL_STORAGE. */
    object NeedsPermission : ExportResult()
}

/**
 * Copies a finished download out of the app-private folder into the shared
 * Downloads/Vidiio collection so it shows up in the system Files/Downloads app,
 * is visible to other apps, and survives uninstalling Vidiio.
 */
object DownloadExporter {

    private const val SUBDIR = "Vidiio"

    suspend fun export(
        context: Context,
        task: DownloadTask,
        hasLegacyStoragePermission: Boolean = false
    ): ExportResult = withContext(Dispatchers.IO) {
        val src = task.filePath?.let { File(it) }
        if (src == null || !src.exists()) {
            return@withContext ExportResult.Error("File not found on disk")
        }

        val displayName = src.name
        val mime = mimeFor(src.extension)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/" + SUBDIR
                    )
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(collection, values)
                    ?: return@withContext ExportResult.Error("Could not create export entry")

                resolver.openOutputStream(uri)?.use { out ->
                    src.inputStream().use { it.copyTo(out) }
                } ?: return@withContext ExportResult.Error("Could not open export stream")

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)

                ExportResult.Success("Downloads/$SUBDIR/$displayName")
            } else {
                if (!hasLegacyStoragePermission) return@withContext ExportResult.NeedsPermission
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    SUBDIR
                ).apply { mkdirs() }
                val dest = File(dir, displayName)
                src.inputStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
                MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf(mime), null)
                ExportResult.Success("Downloads/$SUBDIR/$displayName")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "DownloadExporter.export() failed", e)
            ExportResult.Error(e.message ?: "Export failed")
        }
    }

    private fun mimeFor(ext: String): String = when (ext.lowercase()) {
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "ts" -> "video/mp2t"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "m4v" -> "video/x-m4v"
        else -> "video/mp4"
    }
}

private const val TAG = "DownloadExporter"