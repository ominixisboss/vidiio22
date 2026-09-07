package com.example.vidiio.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class DownloadType {
    TORRENT, HTTP, HLS
}

enum class DownloadStatus {
    QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED
}

@Entity(tableName = "download_tasks")
data class DownloadTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val type: DownloadType,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val filePath: String? = null,
    val totalSize: Long = 0,
    val downloadedSize: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val errorMessage: String? = null,
    /** JSON map of HTTP request headers (Referer/UA) for HTTP downloads. */
    val headersJson: String? = null,
    /** For multi-file torrents: the exact file to fetch (Stremio addon selection). */
    val torrentFileIndex: Int? = null,
    val torrentFileName: String? = null
)

class DownloadConverters {
    @TypeConverter
    fun fromDownloadType(value: DownloadType): String = value.name

    @TypeConverter
    fun toDownloadType(value: String): DownloadType = DownloadType.valueOf(value)

    @TypeConverter
    fun fromDownloadStatus(value: DownloadStatus): String = value.name

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus = DownloadStatus.valueOf(value)
}
