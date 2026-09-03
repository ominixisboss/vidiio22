package com.example.vidiio.data.db

import androidx.room.*
import com.example.vidiio.data.model.DownloadStatus
import com.example.vidiio.data.model.DownloadTask
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadTask>>

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getDownloadById(id: Long): DownloadTask?

    @Query("SELECT * FROM download_tasks WHERE status = 'DOWNLOADING' OR status = 'QUEUED'")
    suspend fun getActiveDownloads(): List<DownloadTask>

    @Query("SELECT * FROM download_tasks WHERE url = :url")
    suspend fun getDownloadByUrl(url: String): DownloadTask?

    @Query("SELECT * FROM download_tasks WHERE url = :url")
    fun getDownloadByUrlFlow(url: String): Flow<DownloadTask?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(task: DownloadTask): Long

    @Update
    suspend fun updateDownload(task: DownloadTask)

    @Delete
    suspend fun deleteDownload(task: DownloadTask)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteDownloadById(id: Long)

    @Query("UPDATE download_tasks SET status = :status, progress = :progress, downloadedSize = :downloadedSize WHERE id = :id")
    suspend fun updateProgress(id: Long, status: DownloadStatus, progress: Float, downloadedSize: Long)

    @Query("UPDATE download_tasks SET status = 'PAUSED' WHERE status = 'DOWNLOADING'")
    suspend fun pauseAllDownloading()
}
