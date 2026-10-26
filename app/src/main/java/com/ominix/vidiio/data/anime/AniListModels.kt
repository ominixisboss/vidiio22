package com.ominix.vidiio.data.anime

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** AniList GraphQL types, trimmed to what the Anime page shows. */

@JsonClass(generateAdapter = true)
data class AniTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
)

@JsonClass(generateAdapter = true)
data class AniCover(
    val extraLarge: String? = null,
    val large: String? = null,
    val color: String? = null,
)

@JsonClass(generateAdapter = true)
data class AniAiring(
    val episode: Int = 0,
    /** Unix seconds. */
    val airingAt: Long = 0L,
)

@JsonClass(generateAdapter = true)
data class AniStudio(val name: String = "")

@JsonClass(generateAdapter = true)
data class AniStudios(val nodes: List<AniStudio> = emptyList())

@JsonClass(generateAdapter = true)
data class AniDate(val year: Int? = null)

@JsonClass(generateAdapter = true)
data class AniTrailer(val id: String? = null, val site: String? = null)

@JsonClass(generateAdapter = true)
data class AniMedia(
    val id: Int = 0,
    val idMal: Int? = null,
    val title: AniTitle = AniTitle(),
    val coverImage: AniCover = AniCover(),
    val bannerImage: String? = null,
    val description: String? = null,
    val format: String? = null,
    val status: String? = null,
    val episodes: Int? = null,
    val duration: Int? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    /** 0-100. */
    val averageScore: Int? = null,
    /** How many AniList users have it on a list. */
    val popularity: Int? = null,
    val trending: Int? = null,
    val genres: List<String> = emptyList(),
    val studios: AniStudios = AniStudios(),
    val nextAiringEpisode: AniAiring? = null,
    val startDate: AniDate? = null,
    val trailer: AniTrailer? = null,
    val isAdult: Boolean = false,
) {
    /** English title when there is one, otherwise romaji - what Crunchyroll and friends show. */
    val displayTitle: String
        get() = title.english?.takeIf { it.isNotBlank() }
            ?: title.romaji?.takeIf { it.isNotBlank() }
            ?: title.native.orEmpty().ifBlank { "Untitled" }

    val posterUrl: String? get() = coverImage.extraLarge ?: coverImage.large

    val year: Int? get() = seasonYear ?: startDate?.year

    /** Score out of 10, one decimal, or null when unrated. */
    val score10: Double? get() = averageScore?.takeIf { it > 0 }?.let { it / 10.0 }

    val isMovie: Boolean get() = format == "MOVIE"

    val mainStudio: String? get() = studios.nodes.firstOrNull()?.name?.takeIf { it.isNotBlank() }
}

@JsonClass(generateAdapter = true)
data class AniScheduleEntry(
    val episode: Int = 0,
    /** Unix seconds. */
    val airingAt: Long = 0L,
    val media: AniMedia = AniMedia(),
)

@JsonClass(generateAdapter = true)
data class AniPageInfo(val hasNextPage: Boolean = false)

@JsonClass(generateAdapter = true)
data class AniPage(
    val pageInfo: AniPageInfo? = null,
    val media: List<AniMedia> = emptyList(),
    val airingSchedules: List<AniScheduleEntry> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AniData(@Json(name = "Page") val page: AniPage? = null)

@JsonClass(generateAdapter = true)
data class AniError(val message: String? = null)

@JsonClass(generateAdapter = true)
data class AniResponse(
    val data: AniData? = null,
    val errors: List<AniError>? = null,
)
