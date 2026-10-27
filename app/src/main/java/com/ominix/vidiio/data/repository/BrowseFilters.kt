package com.ominix.vidiio.data.repository

/** How a filtered Movies / TV result list is ordered. */
enum class BrowseSort(val label: String) {
    POPULAR("Popular"),
    TOP_RATED("Top rated"),
    NEWEST("Newest"),
}

/** A decade to narrow results to, e.g. the 1990s. */
data class Decade(val label: String, val startYear: Int) {
    val endYear: Int get() = startYear + 9
}

/** A mood: a handful of genres that suit it. TMDB reads `|` as "any of these". */
data class Mood(val label: String, val genreIds: List<Int>) {
    val genreExpr: String get() = genreIds.joinToString("|")
}

/** A studio (movies) or a network / streamer (TV), by TMDB id. */
data class Provider(val label: String, val id: Int)

/** Everything the Movies and TV pages can filter by at once. Null means "don't filter on this". */
data class BrowseFilter(
    val genreId: Int? = null,
    val mood: Mood? = null,
    val decade: Decade? = null,
    val provider: Provider? = null,
    val sort: BrowseSort = BrowseSort.POPULAR,
) {
    /** True when nothing narrows the list, so the page shows its normal shelves instead. */
    val isEmpty: Boolean get() = genreId == null && mood == null && decade == null && provider == null

    /** Genre as TMDB wants it: a specific genre wins over a mood's "any of". */
    val genreExpr: String? get() = genreId?.toString() ?: mood?.genreExpr
}

object BrowseOptions {
    val decades = listOf(
        Decade("2020s", 2020), Decade("2010s", 2010), Decade("2000s", 2000),
        Decade("1990s", 1990), Decade("1980s", 1980), Decade("1970s", 1970),
        Decade("1960s", 1960), Decade("Classics", 1900),
    )

    val movieMoods = listOf(
        Mood("Feel-good", listOf(35, 10751, 10749)),
        Mood("Edge of your seat", listOf(53, 80, 9648)),
        Mood("Mind-bending", listOf(878, 9648)),
        Mood("Date night", listOf(10749, 35)),
        Mood("Family night", listOf(10751, 16)),
        Mood("Scary", listOf(27)),
        Mood("Epic", listOf(12, 14, 10752, 36)),
        Mood("Based on real life", listOf(36, 99)),
    )

    val tvMoods = listOf(
        Mood("Binge-worthy", listOf(18, 9648, 80)),
        Mood("Feel-good", listOf(35, 10751)),
        Mood("True crime", listOf(80, 99)),
        Mood("Mind-bending", listOf(10765, 9648)),
        Mood("Family night", listOf(10751, 16, 10762)),
        Mood("Epic worlds", listOf(10765, 10759)),
    )

    /** Movie studios, TMDB company ids. */
    val studios = listOf(
        Provider("Marvel Studios", 420), Provider("Pixar", 3), Provider("Disney", 2),
        Provider("Warner Bros.", 174), Provider("Universal", 33), Provider("A24", 41077),
        Provider("Blumhouse", 3172), Provider("Lucasfilm", 1), Provider("DreamWorks", 521),
        Provider("Studio Ghibli", 10342), Provider("Paramount", 4), Provider("Sony Pictures", 34),
    )

    /** TV networks and streamers, TMDB network ids. */
    val networks = listOf(
        Provider("Netflix", 213), Provider("HBO", 49), Provider("Disney+", 2739),
        Provider("Apple TV+", 2552), Provider("Prime Video", 1024), Provider("Hulu", 453),
        Provider("BBC", 4), Provider("AMC", 174), Provider("FX", 88), Provider("Paramount+", 4330),
    )

    /** TMDB genres missing from the first version of the Movies page. */
    val extraMovieGenres = listOf(10770 to "TV Movie")
}

/**
 * Bayesian weighted rating, the way IMDb ranks its Top 250: a title with few votes is pulled
 * toward the overall mean, so a film rated 9.0 by twelve people does not beat one rated 8.4 by
 * twelve thousand.
 */
fun weightedRating(average: Double, votes: Int, minVotes: Double = 1000.0, meanVote: Double = 6.5): Double {
    val v = votes.toDouble()
    return (v / (v + minVotes)) * average + (minVotes / (v + minVotes)) * meanVote
}
