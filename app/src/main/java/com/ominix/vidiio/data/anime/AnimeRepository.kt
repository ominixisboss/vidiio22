package com.ominix.vidiio.data.anime

import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.MovieType
import com.ominix.vidiio.data.repository.MovieRepository
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max

/** AniList's four broadcast seasons. */
enum class AnimeSeason {
    WINTER, SPRING, SUMMER, FALL;

    fun next(): AnimeSeason = entries[(ordinal + 1) % entries.size]

    companion object {
        /** Calendar month (0-11) to season: Jan-Mar winter, Apr-Jun spring, Jul-Sep summer, Oct-Dec fall. */
        fun ofMonth(month: Int): AnimeSeason = entries[(month / 3).coerceIn(0, 3)]
    }
}

/** A season plus the year it airs in. */
data class SeasonYear(val season: AnimeSeason, val year: Int) {
    fun next(): SeasonYear =
        if (season == AnimeSeason.FALL) SeasonYear(AnimeSeason.WINTER, year + 1)
        else SeasonYear(season.next(), year)

    companion object {
        fun at(epochMillis: Long, zone: TimeZone = TimeZone.getDefault()): SeasonYear {
            val cal = Calendar.getInstance(zone).apply { timeInMillis = epochMillis }
            return SeasonYear(AnimeSeason.ofMonth(cal.get(Calendar.MONTH)), cal.get(Calendar.YEAR))
        }
    }
}

/**
 * Everything the Anime page shows, fetched from AniList and cached briefly in memory.
 * Playback still goes through the app's normal pipeline: [resolve] maps a title onto the TMDB
 * entry the details page and scrapers already work with.
 */
class AnimeRepository(
    private val service: AniListService,
    private val movieRepository: MovieRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val at: Long, val value: Any)

    private val cache = ConcurrentHashMap<String, Entry>()

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(key: String, force: Boolean, ttlMs: Long = TTL_MS, load: suspend () -> T): T {
        val now = clock()
        if (!force) {
            val hit = cache[key]
            if (hit != null && now - hit.at < ttlMs) return hit.value as T
        }
        return load().also { cache[key] = Entry(now, it) }
    }

    private val season: SeasonYear get() = SeasonYear.at(clock())

    /** Bundled anime addons (One Pace) as shelves of titles that open straight to details. */
    suspend fun addonShelves(force: Boolean = false) =
        cached("addons", force) { movieRepository.getAnimeAddonCategories() }

    /** What everyone is watching right now: AniList's trending score. */
    suspend fun trending(force: Boolean = false) =
        cached("trending", force) { service.media(sort = "TRENDING_DESC", perPage = 24) }

    /** Simulcasts: currently airing, most popular first. Only ones with an upcoming episode. */
    suspend fun airingNow(force: Boolean = false) = cached("airing", force) {
        service.media(sort = "POPULARITY_DESC", perPage = 30, status = "RELEASING")
            .filter { it.nextAiringEpisode != null }
    }

    suspend fun popularThisSeason(force: Boolean = false) = cached("popularSeason", force) {
        service.media(sort = "POPULARITY_DESC", perPage = 24, season = season.season.name, seasonYear = season.year)
    }

    suspend fun topRated(force: Boolean = false) = cached("topRated", force) {
        service.media(sort = "SCORE_DESC", perPage = 24, minPopularity = 60_000)
    }

    suspend fun allTimePopular(force: Boolean = false) =
        cached("allTime", force) { service.media(sort = "POPULARITY_DESC", perPage = 24) }

    suspend fun nextSeason(force: Boolean = false) = cached("nextSeason", force) {
        val next = season.next()
        service.media(sort = "POPULARITY_DESC", perPage = 24, season = next.season.name, seasonYear = next.year)
    }

    suspend fun byGenre(genre: String, force: Boolean = false) = cached("genre:$genre", force) {
        service.media(sort = "POPULARITY_DESC", perPage = 24, genre = genre, minPopularity = 15_000)
    }

    /** Episodes that aired in the last day and a half, newest first, one entry per show. */
    suspend fun newEpisodes(force: Boolean = false) = cached("newEpisodes", force, ttlMs = SCHEDULE_TTL_MS) {
        val now = clock() / 1000
        val entries = buildList {
            var page = 1
            do {
                val (items, more) = service.schedule(from = now - NEW_WINDOW_S, to = now, descending = true, page = page)
                addAll(items)
                page++
            } while (more && page <= 2)
        }
        entries.distinctBy { it.media.id }
    }

    /** The next seven days of episodes, in air order, for the weekly calendar. */
    suspend fun weekSchedule(force: Boolean = false) = cached("week", force, ttlMs = SCHEDULE_TTL_MS) {
        val from = startOfToday(clock())
        val to = from + 7 * DAY_S
        buildList {
            var page = 1
            do {
                val (items, more) = service.schedule(from = from, to = to, descending = false, page = page)
                addAll(items)
                page++
            } while (more && page <= 4)
        }
    }

    /**
     * Finds the TMDB entry for an AniList title, so the existing details page, episode list and
     * source scrapers can be used unchanged. Null when nothing matches well enough.
     */
    suspend fun resolve(media: AniMedia): Movie? {
        val queries = queryVariants(media)
        val wantTv = !media.isMovie
        val candidates = mutableListOf<Pair<Movie, Int>>()
        for (query in queries) {
            movieRepository.search(query)
                .filter { (it.type == MovieType.TV_SHOW) == wantTv && it.posterUrl.isNotBlank() }
                .forEach { movie ->
                    val score = matchScore(movie.title, movie.year, queries, media.year)
                    if (score >= MIN_MATCH_SCORE) candidates += movie to score
                }
            // An exact hit needs no further searching.
            if (candidates.any { it.second >= 100 }) break
        }
        return candidates.maxByOrNull { it.second }?.first
    }

    companion object {
        private const val TTL_MS = 10 * 60 * 1000L
        private const val SCHEDULE_TTL_MS = 5 * 60 * 1000L
        private const val DAY_S = 24 * 60 * 60L
        private const val NEW_WINDOW_S = 36 * 60 * 60L
        internal const val MIN_MATCH_SCORE = 55

        /** Local midnight today, unix seconds. */
        internal fun startOfToday(nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long {
            val cal = Calendar.getInstance(zone).apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis / 1000
        }

        /**
         * The titles to search TMDB for, most specific first. AniList titles carry an arc or season
         * ("... - The Calamity", "... Season 2") that TMDB search does not know, and TMDB often
         * has the series under the shorter name - so also try the title with those stripped.
         */
        internal fun queryVariants(media: AniMedia): List<String> {
            val seasonSuffix = Regex("""(?i)\s+(season\s*\d+|\d+(st|nd|rd|th)\s+season|part\s*\d+|cour\s*\d+)\s*$""")
            val full = listOfNotNull(media.title.english, media.title.romaji).filter { it.isNotBlank() }
            val stripped = full.flatMap { title ->
                val beforeDash = title.substringBefore(" - ").trim()
                listOf(beforeDash, beforeDash.replace(seasonSuffix, "").trim(), title.replace(seasonSuffix, "").trim())
            }
            return (full + stripped).filter { it.isNotBlank() }.distinct().take(5)
        }

        internal fun normalizeTitle(title: String): String =
            title.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

        /**
         * How well a TMDB result's title fits the AniList title(s). AniList often appends the season
         * ("... Season 2") where TMDB has one entry for the whole series, so a prefix counts.
         */
        internal fun matchScore(candidateTitle: String, candidateYear: Int?, queries: List<String>, year: Int?): Int {
            val candidate = normalizeTitle(candidateTitle)
            if (candidate.isEmpty()) return 0
            var best = 0
            for (query in queries) {
                val q = normalizeTitle(query)
                if (q.isEmpty()) continue
                val score = when {
                    candidate == q -> 100
                    // One title is the start of the other (AniList appends a season or arc name, TMDB often
                    // has the bare series). The more of the longer title the shorter one covers, the better,
                    // so "Bleach: Thousand-Year Blood War" beats plain "Bleach" for a Blood War season.
                    q.startsWith("$candidate ") || candidate.startsWith("$q ") ->
                        50 + (40.0 * minOf(candidate.length, q.length) / maxOf(candidate.length, q.length)).toInt()
                    (candidate.length >= 4 && candidate in q) || (q.length >= 4 && q in candidate) -> 55
                    else -> 0
                }
                best = max(best, score)
            }
            if (best > 0 && year != null && candidateYear != null && abs(year - candidateYear) <= 1) best += 15
            return best
        }
    }
}
