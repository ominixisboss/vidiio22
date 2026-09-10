package com.ominix.vidiio.data.model.subtitles

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SubdlResponse(
    val status: Boolean,
    val subtitles: List<SubdlSubtitle>? = null
)

@JsonClass(generateAdapter = true)
data class SubdlSubtitle(
    val id: String,
    val name: String,
    val language: String,
    @Json(name = "release_name") val releaseName: String?,
    @Json(name = "url") val url: String?
)

@JsonClass(generateAdapter = true)
data class SubdlSearchResponse(
    val status: Boolean,
    val results: List<SubdlSearchResult>? = null
)

@JsonClass(generateAdapter = true)
data class SubdlSearchResult(
    val id: Int,
    val name: String,
    val type: String,
    @Json(name = "imdb_id") val imdbId: String?,
    @Json(name = "tmdb_id") val tmdbId: Int?
)
