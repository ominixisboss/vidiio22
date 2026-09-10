package com.ominix.vidiio.data.repository

import com.ominix.vidiio.data.db.FavoriteDao
import com.ominix.vidiio.data.model.FavoriteMovie
import kotlinx.coroutines.flow.Flow

class FavoriteRepository(private val favoriteDao: FavoriteDao) {
    val favorites: Flow<List<FavoriteMovie>> = favoriteDao.getAllFavorites()

    fun isFavorite(movieId: String): Flow<Boolean> = favoriteDao.isFavorite(movieId)

    suspend fun addFavorite(movie: FavoriteMovie) {
        favoriteDao.insert(movie)
    }

    suspend fun removeFavorite(movie: FavoriteMovie) {
        favoriteDao.delete(movie)
    }
}
