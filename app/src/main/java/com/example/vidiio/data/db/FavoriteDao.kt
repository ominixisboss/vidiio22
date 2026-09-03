package com.example.vidiio.data.db

import androidx.room.*
import com.example.vidiio.data.model.FavoriteMovie
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_movies ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteMovie>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_movies WHERE id = :movieId)")
    fun isFavorite(movieId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(movie: FavoriteMovie)

    @Delete
    suspend fun delete(movie: FavoriteMovie)
}
