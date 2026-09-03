package com.example.vidiio.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Movie(
    val id: String,
    val title: String,
    val posterUrl: String,
    val synopsis: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    val type: MovieType = MovieType.MOVIE,
    val source: String, // e.g., tmdb, cinejoy, vidsrc
    val seasons: List<Season> = emptyList()
)

@Serializable
data class Season(
    val seasonNumber: Int,
    val episodes: List<Episode>
)

@Serializable
data class Episode(
    val id: String,
    val name: String,
    val overview: String? = null,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val stillPath: String? = null
)

enum class MovieType {
    MOVIE, TV_SHOW
}
