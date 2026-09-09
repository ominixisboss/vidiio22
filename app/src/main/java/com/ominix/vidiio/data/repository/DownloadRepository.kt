package com.ominix.vidiio.data.repository

import com.ominix.vidiio.data.db.DownloadDao
import com.ominix.vidiio.data.model.DownloadStatus
import com.ominix.vidiio.data.model.DownloadTask
import kotlinx.coroutines.flow.Flow

class DownloadRepository(private val downloadDao: DownloadDao) {
    val allDownloads: Flow<List<DownloadTask>> = downloadDao.getAllDownloads()

    suspend fun getDownloadById(id: Long): DownloadTask? = downloadDao.getDownloadById(id)

    suspend fun getDownloadByUrl(url: String): DownloadTask? = downloadDao.getDownloadByUrl(url)

    fun getDownloadByUrlFlow(url: String): Flow<DownloadTask?> = downloadDao.getDownloadByUrlFlow(url)

    suspend fun getActiveDownloads(): List<DownloadTask> = downloadDao.getActiveDownloads()

    suspend fun insertDownload(task: DownloadTask): Long = downloadDao.insertDownload(task)

    suspend fun updateDownload(task: DownloadTask) = downloadDao.updateDownload(task)

    suspend fun deleteDownload(task: DownloadTask) = downloadDao.deleteDownload(task)

    suspend fun deleteDownloadById(id: Long) = downloadDao.deleteDownloadById(id)

    suspend fun updateProgress(id: Long, status: DownloadStatus, progress: Float, downloadedSize: Long) {
        downloadDao.updateProgress(id, status, progress, downloadedSize)
    }

    suspend fun pauseAllDownloading() = downloadDao.pauseAllDownloading()
}
