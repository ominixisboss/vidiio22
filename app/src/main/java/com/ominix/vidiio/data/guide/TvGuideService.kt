package com.ominix.vidiio.data.guide

import com.ominix.vidiio.data.http.awaitResponse
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@JsonClass(generateAdapter = true)
data class TvmImage(val medium: String? = null, val original: String? = null)

@JsonClass(generateAdapter = true)
data class TvmChannel(val name: String? = null)

@JsonClass(generateAdapter = true)
data class TvmRating(val average: Double? = null)

@JsonClass(generateAdapter = true)
data class TvmShow(
    val id: Int = 0,
    val name: String = "",
    val type: String? = null,
    val language: String? = null,
    val network: TvmChannel? = null,
    val webChannel: TvmChannel? = null,
    val image: TvmImage? = null,
    val rating: TvmRating? = null,
    val premiered: String? = null,
)

@JsonClass(generateAdapter = true)
data class TvmEmbedded(val show: TvmShow? = null)

@JsonClass(generateAdapter = true)
data class TvmEpisode(
    val id: Int = 0,
    val name: String? = null,
    val season: Int? = null,
    val number: Int? = null,
    val airstamp: String? = null,
    val show: TvmShow? = null,
    val _embedded: TvmEmbedded? = null,
)

/** One show airing at one time, ready to draw. */
data class GuideEntry(
    val showName: String,
    val episodeTitle: String?,
    val season: Int?,
    val episode: Int?,
    val channel: String?,
    val posterUrl: String?,
    val rating: Double?,
    val year: Int?,
    /** Local time it airs, or null when only the day is known. */
    val time: java.time.LocalTime?,
    val isStreaming: Boolean,
) {
    val episodeCode: String?
        get() = if (season != null && episode != null) "S%02d E%02d".format(Locale.US, season, episode) else null
}

/**
 * The TV Guide's data: what airs on a day, with times and episode numbers, from TVmaze's free
 * public schedule (no key). TMDB can say a show airs today but not when or which episode.
 */
class TvGuideService(
    private val client: OkHttpClient,
    moshi: Moshi = Moshi.Builder().build(),
) {
    private val adapter = moshi.adapter<List<TvmEpisode>>(Types.newParameterizedType(List::class.java, TvmEpisode::class.java))

    suspend fun day(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<GuideEntry> {
        val iso = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        // Broadcast and streaming schedules are separate lists; either may fail on its own.
        val broadcast = runCatching { fetch("https://api.tvmaze.com/schedule?country=US&date=$iso") }
        val streaming = runCatching { fetch("https://api.tvmaze.com/schedule/web?country=US&date=$iso") }
        if (broadcast.isFailure && streaming.isFailure) throw (broadcast.exceptionOrNull() ?: IOException("TVmaze unavailable"))

        val entries = buildList {
            broadcast.getOrNull()?.forEach { toEntry(it, it.show, false, zone, date)?.let(::add) }
            streaming.getOrNull()?.forEach { toEntry(it, it._embedded?.show, true, zone, date)?.let(::add) }
        }
        return rank(entries)
    }

    private suspend fun fetch(url: String): List<TvmEpisode> {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).awaitResponse().use { response ->
            if (!response.isSuccessful) throw IOException("TVmaze HTTP ${response.code}")
            return adapter.fromJson(response.body?.string().orEmpty()).orEmpty()
        }
    }

    companion object {
        /** Shows that are not a watchable programme: talk, news, sports and game shows fill the list otherwise. */
        private val SKIPPED_TYPES = setOf("News", "Sports", "Talk Show", "Game Show", "Variety", "Panel Show")

        internal fun toEntry(
            episode: TvmEpisode,
            show: TvmShow?,
            streaming: Boolean,
            zone: ZoneId,
            date: LocalDate,
        ): GuideEntry? {
            show ?: return null
            if (show.type in SKIPPED_TYPES) return null
            // Daily programmes number their "seasons" by year (S2026 E215): soaps and news blocks, not shows to follow.
            if ((episode.season ?: 0) > 1900) return null
            if (show.network?.name?.contains("News", ignoreCase = true) == true) return null
            if (!show.language.isNullOrBlank() && show.language != "English") return null
            val poster = (show.image?.medium ?: show.image?.original)?.replaceFirst("http://", "https://")
            if (poster.isNullOrBlank()) return null
            val local = runCatching { OffsetDateTime.parse(episode.airstamp).atZoneSameInstant(zone) }.getOrNull()
            // Streaming drops are stamped at a nominal time; only the day matters, and only the right day.
            if (local != null && local.toLocalDate() != date) return null
            return GuideEntry(
                showName = show.name,
                episodeTitle = episode.name,
                season = episode.season,
                episode = episode.number,
                channel = show.network?.name ?: show.webChannel?.name,
                posterUrl = poster,
                rating = show.rating?.average,
                year = show.premiered?.take(4)?.toIntOrNull(),
                time = if (streaming) null else local?.toLocalTime(),
                isStreaming = streaming,
            )
        }

        /** One row per show, timed shows first in time order, then streaming, best rated first within a slot. */
        internal fun rank(entries: List<GuideEntry>): List<GuideEntry> =
            entries.distinctBy { it.showName }
                .sortedWith(
                    compareBy<GuideEntry> { it.time == null }
                        .thenBy { it.time }
                        .thenByDescending { it.rating ?: 0.0 }
                )
    }
}
