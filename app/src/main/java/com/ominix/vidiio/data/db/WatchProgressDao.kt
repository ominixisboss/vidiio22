package com.ominix.vidiio.data.db

import androidx.room.*
import com.ominix.vidiio.data.model.WatchProgress
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchProgressDao {
    @Query("SELECT * FROM watch_progress ORDER BY updatedAt DESC LIMIT 20")
    fun getRecent(): Flow<List<WatchProgress>>

    @Query("SELECT * FROM watch_progress WHERE id = :id LIMIT 1")
    suspend fun get(id: String): WatchProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: WatchProgress)

    @Query("DELETE FROM watch_progress WHERE id = :id")
    suspend fun delete(id: String)
}
