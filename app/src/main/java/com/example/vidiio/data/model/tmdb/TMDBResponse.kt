package com.example.vidiio.data.model.tmdb

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TMDBResponse<T>(
    @Json(name = "results") val results: List<T>
)

@JsonClass(generateAdapter = true)
data class TMDBMovie(
    @Json(name = "id") val id: Int,
    @Json(name = "title") val title: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "overview") val overview: String?,
    @Json(name = "vote_average") val voteAverage: Double?,
    @Json(name = "release_date") val releaseDate: String?,
    @Json(name = "first_air_date") val firstAirDate: String?,
    @Json(name = "media_type") val mediaType: String?,
    @Json(name = "imdb_id") val imdbId: String? = null,
    @Json(name = "external_ids") val externalIds: TMDBExternalIds? = null,
    @Json(name = "seasons") val seasons: List<TMDBSeason>? = null
)

@JsonClass(generateAdapter = true)
data class TMDBSeason(
    @Json(name = "id") val id: Int,
    @Json(name = "season_number") val seasonNumber: Int,
    @Json(name = "episodes") val episodes: List<TMDBEpisode>? = null,
    @Json(name = "name") val name: String?,
    @Json(name = "poster_path") val posterPath: String?
)

@JsonClass(generateAdapter = true)
data class TMDBEpisode(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "overview") val overview: String?,
    @Json(name = "episode_number") val episodeNumber: Int,
    @Json(name = "season_number") val seasonNumber: Int,
    @Json(name = "still_path") val stillPath: String?
)

@JsonClass(generateAdapter = true)
data class TMDBExternalIds(
    @Json(name = "imdb_id") val imdbId: String?
)
