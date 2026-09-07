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
    @Json(name = "seasons") val seasons: List<TMDBSeason>? = null,
    @Json(name = "videos") val videos: TMDBVideoList? = null
)

@JsonClass(generateAdapter = true)
data class TMDBVideoList(
    @Json(name = "results") val results: List<TMDBVideo> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TMDBVideo(
    @Json(name = "key") val key: String,
    @Json(name = "site") val site: String?,
    @Json(name = "type") val type: String?,
    @Json(name = "official") val official: Boolean = false,
    @Json(name = "name") val name: String? = null
)

/** Best YouTube trailer key, if any: official Trailer → any Trailer → Teaser → any YouTube clip. */
fun TMDBVideoList?.bestTrailerKey(): String? {
    val yt = this?.results?.filter { it.site.equals("YouTube", true) && it.key.isNotBlank() } ?: return null
    return yt.firstOrNull { it.type.equals("Trailer", true) && it.official }?.key
        ?: yt.firstOrNull { it.type.equals("Trailer", true) }?.key
        ?: yt.firstOrNull { it.type.equals("Teaser", true) }?.key
        ?: yt.firstOrNull()?.key
}

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
