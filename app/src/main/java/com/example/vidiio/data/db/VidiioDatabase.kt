package com.example.vidiio.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.vidiio.data.model.DownloadConverters
import com.example.vidiio.data.model.DownloadTask
import com.example.vidiio.data.model.FavoriteMovie

@Database(entities = [FavoriteMovie::class, DownloadTask::class], version = 2, exportSchema = false)
@TypeConverters(DownloadConverters::class)
abstract class VidiioDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun downloadDao(): DownloadDao
}
