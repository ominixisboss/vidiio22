package com.ominix.vidiio.download

import com.ominix.vidiio.data.model.DownloadTask
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HttpDownloader(private val okHttpClient: OkHttpClient) {

    suspend fun download(
        task: DownloadTask,
        destDir: File,
        onProgress: (progress: Float, downloaded: Long, total: Long) -> Unit,
        isCancelled: () -> Boolean
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder().url(task.url)
            builder.header("User-Agent", DEFAULT_UA)
            task.headersJson?.let { json ->
                runCatching {
                    val obj = JSONObject(json)
                    obj.keys().forEach { k -> builder.header(k, obj.getString(k)) }
                }
            }
            val response = okHttpClient.newCall(builder.build()).execute()

            if (!response.isSuccessful) {
                return@withContext DownloadResult.Error("Failed to connect: ${response.code}")
            }

            val body = response.body ?: return@withContext DownloadResult.Error("Empty response body")
            val totalSize = body.contentLength()
            val inputStream: InputStream = body.byteStream()

            val ext = when {
                task.url.substringBefore('?').endsWith(".mkv", true) -> "mkv"
                task.url.substringBefore('?').endsWith(".webm", true) -> "webm"
                body.contentType()?.subtype == "x-matroska" -> "mkv"
                else -> "mp4"
            }
            val safeTitle = task.title
                .replace(Regex("\\.(mp4|mkv|webm|avi|mov|m4v)$", RegexOption.IGNORE_CASE), "")
                .replace(Regex("[^a-zA-Z0-9.\\- ]"), "_")
                .trim()
                .ifEmpty { "video" }
            val file = File(destDir, "$safeTitle.$ext")
            val outputStream = FileOutputStream(file)

            val buffer = ByteArray(8192)
            var downloaded: Long = 0
            var read: Int

            while (inputStream.read(buffer).also { read = it } != -1) {
                if (isCancelled()) {
                    outputStream.close()
                    inputStream.close()
                    return@withContext DownloadResult.Cancelled
                }
                outputStream.write(buffer, 0, read)
                downloaded += read
                val progress = if (totalSize > 0) (downloaded.toFloat() / totalSize) * 100 else 0f
                onProgress(progress, downloaded, totalSize)
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            DownloadResult.Success(file.absolutePath, totalSize)
        } catch (e: Exception) {
            DownloadResult.Error(e.message ?: "Unknown error")
        }
    }

    private companion object {
        const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}

sealed class DownloadResult {
    data class Success(val filePath: String, val totalSize: Long) : DownloadResult()
    data class Error(val message: String) : DownloadResult()
    object Cancelled : DownloadResult()
}
