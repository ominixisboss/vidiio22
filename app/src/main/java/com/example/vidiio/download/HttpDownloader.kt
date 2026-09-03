package com.example.vidiio.download

import com.example.vidiio.data.model.DownloadStatus
import com.example.vidiio.data.model.DownloadTask
import okhttp3.OkHttpClient
import okhttp3.Request
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
            val request = Request.Builder().url(task.url).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext DownloadResult.Error("Failed to connect: ${response.code}")
            }

            val body = response.body ?: return@withContext DownloadResult.Error("Empty response body")
            val totalSize = body.contentLength()
            val inputStream: InputStream = body.byteStream()
            
            val fileName = task.title.replace(Regex("[^a-zA-Z0-9.\\-]"), "_") + ".mp4"
            val file = File(destDir, fileName)
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
}

sealed class DownloadResult {
    data class Success(val filePath: String, val totalSize: Long) : DownloadResult()
    data class Error(val message: String) : DownloadResult()
    object Cancelled : DownloadResult()
}
