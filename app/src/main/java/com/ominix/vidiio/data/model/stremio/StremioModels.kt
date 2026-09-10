package com.ominix.vidiio.data.model.stremio

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Manifest(
    val id: String,
    val name: String,
    val description: String? = null,
    val version: String? = null,
    val resources: List<Any>? = null, // Can be String or Resource object
    val types: List<String>? = null,
    val catalogs: List<Catalog>? = null,
    val background: String? = null,
    val logo: String? = null,
    val contactEmail: String? = null
)

@JsonClass(generateAdapter = true)
data class Resource(
    val name: String,
    val types: List<String>,
    val idPrefixes: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class Catalog(
    val type: String,
    val id: String,
    val name: String? = null,
    val extra: List<CatalogExtra>? = null
)

@JsonClass(generateAdapter = true)
data class CatalogExtra(
    val name: String,
    val isRequired: Boolean = false,
    val options: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class StreamResponse(
    val streams: List<StremioStream>
)

@JsonClass(generateAdapter = true)
data class CatalogResponse(
    val metas: List<StremioMeta>
)

@JsonClass(generateAdapter = true)
data class StremioMeta(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val genres: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class MetaResponse(
    val meta: StremioMetaDetail
)

@JsonClass(generateAdapter = true)
data class StremioMetaDetail(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val genres: List<String>? = null,
    val videos: List<StremioVideo>? = null
)

@JsonClass(generateAdapter = true)
data class StremioVideo(
    val id: String,
    val title: String? = null,
    val name: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val overview: String? = null,
    val thumbnail: String? = null,
    val released: String? = null
)

@JsonClass(generateAdapter = true)
data class StremioStream(
    val name: String? = null,
    val title: String? = null,
    val url: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val behaviorHints: Map<String, Any>? = null
)
