package com.ominix.vidiio.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_movies")
data class FavoriteMovie(
    @PrimaryKey val id: String,
    val title: String,
    val posterUrl: String,
    val synopsis: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    val type: String, // MOVIE or TV_SHOW
    val source: String,
    val addedAt: Long = System.currentTimeMillis()
)

fun FavoriteMovie.toMovie() = Movie(
    id = id,
    title = title,
    posterUrl = posterUrl,
    synopsis = synopsis,
    year = year,
    rating = rating,
    type = MovieType.valueOf(type),
    source = source
)

fun Movie.toFavoriteMovie() = FavoriteMovie(
    id = id,
    title = title,
    posterUrl = posterUrl,
    synopsis = synopsis,
    year = year,
    rating = rating,
    type = type.name,
    source = source
)
