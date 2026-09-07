package com.example.vidiio.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.vidiio.data.model.DownloadConverters
import com.example.vidiio.data.model.DownloadTask
import com.example.vidiio.data.model.FavoriteMovie
import com.example.vidiio.data.model.WatchProgress

@Database(entities = [FavoriteMovie::class, DownloadTask::class, WatchProgress::class], version = 4, exportSchema = false)
@TypeConverters(DownloadConverters::class)
abstract class VidiioDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun downloadDao(): DownloadDao
    abstract fun watchProgressDao(): WatchProgressDao
}
