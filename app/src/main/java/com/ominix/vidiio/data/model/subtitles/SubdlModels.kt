package com.ominix.vidiio.data.model.subtitles

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SubdlResponse(
    val status: Boolean = false,
    val subtitles: List<SubdlSubtitle>? = null
)

/**
 * One SubDL search hit.
 *
 * Every field is nullable with a default on purpose. These were previously declared
 * non-null, and Moshi's generated adapter throws on a missing non-null field - so a
 * single result lacking, say, `id` failed the whole response, which the caller then
 * swallowed as "no subtitles". SubDL omits fields freely depending on the query.
 */
@JsonClass(generateAdapter = true)
data class SubdlSubtitle(
    val id: String? = null,
    val name: String? = null,
    val language: String? = null,
    val lang: String? = null,
    @Json(name = "release_name") val releaseName: String? = null,
    val url: String? = null,
    val format: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val hi: Boolean? = null,
    @Json(name = "full_season") val fullSeason: Boolean? = null,
    @Json(name = "unpack_files") val unpackFiles: List<SubdlUnpackFile>? = null
)

/**
 * A single subtitle file inside a packed/full-season upload, returned when the request
 * asks for `unpack=1`.
 *
 * This is the only playable form SubDL offers: the top-level `url` is a .zip, which
 * ExoPlayer cannot read.
 */
@JsonClass(generateAdapter = true)
data class SubdlUnpackFile(
    @Json(name = "file_n_id") val fileNId: String? = null,
    val name: String? = null,
    @Json(name = "release_name") val releaseName: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val language: String? = null,
    val hi: Boolean? = null,
    val format: String? = null,
    val url: String? = null
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
