package com.ominix.vidiio.data.repository

import com.ominix.vidiio.data.api.TMDBService
import com.ominix.vidiio.data.model.Category
import com.ominix.vidiio.data.model.TOP10_LABEL
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.MovieType
import com.ominix.vidiio.data.model.Season
import com.ominix.vidiio.data.model.Episode
import com.ominix.vidiio.data.model.StreamSource
import com.ominix.vidiio.data.model.stremio.StremioMeta
import com.ominix.vidiio.data.model.tmdb.TMDBMovie
import com.ominix.vidiio.data.model.tmdb.bestTrailerKey
import com.ominix.vidiio.data.model.tmdb.TMDBSeason
import com.ominix.vidiio.data.model.tmdb.TMDBEpisode
import com.ominix.vidiio.data.scraper.Scraper
import com.ominix.vidiio.data.stremio.AddonManager
import com.ominix.vidiio.data.stremio.toStreamSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import android.util.Log
import kotlinx.coroutines.CancellationException

class MovieRepository(
    private val tmdbService: TMDBService,
    private val scrapers: List<Scraper>,
    private val settingsRepository: SettingsRepository,
    private val addonManager: AddonManager,
    private val sourceVerifier: SourceVerifier
) {

    /** Probes one scraped source for whether it is actually playable. See [SourceVerifier]. */
    suspend fun checkSource(source: StreamSource): SourceHealth = sourceVerifier.check(source)

    private companion object {
        /**
         * Per-scraper budget. Must exceed the OkHttp connect timeout (15s) plus a couple of
         * round-trips — the crypto/multi-request providers (Cinejoy queries 5 servers, VidSrc
         * chains resolvers) reliably need more than the old 10s.
         */
        const val SCRAPER_TIMEOUT_MS = 25_000L

        /** One Pace video-id prefix → arc name, for nicer season labels. */
        val ONE_PACE_ARCS = mapOf(
            "RO" to "Romance Dawn", "OR" to "Orange Town", "SY" to "Syrup Village",
            "GA" to "Gaimon", "BA" to "Baratie", "AR" to "Arlong Park", "LO" to "Loguetown",
            "RM" to "Reverse Mountain", "WH" to "Whisky Peak", "LI" to "Little Garden",
            "DI" to "Drum Island", "AL" to "Arabasta", "JA" to "Jaya", "SK" to "Skypiea",
            "LR" to "Long Ring Long Land", "WS" to "Water Seven", "EN" to "Enies Lobby",
            "PEN" to "Post-Enies Lobby", "TB" to "Thriller Bark", "SAB" to "Sabaody Archipelago",
            "AM" to "Amazon Lily", "IM" to "Impel Down", "MA" to "Marineford", "PW" to "Post-War",
            "RTS" to "Return to Sabaody", "FI" to "Fish-Man Island", "PH" to "Punk Hazard",
            "DR" to "Dressrosa", "ZO" to "Zou", "WC" to "Whole Cake Island", "REV" to "Reverie",
            "WA" to "Wano", "EH" to "Egghead",
        )

    }

    private suspend fun getEnabledScrapers(): List<Scraper> {
        val enabledSources = settingsRepository.sourcesFlow.first()
        return scrapers.filter { it.sourceId in enabledSources }
    }

    /**
     * Home is a short front page, not every shelf: a handful of what is hot, plus anything the
     * user's own addons offer. Everything else (genres, top rated, anime, One Pace, ...) lives on
     * the dedicated Movies, TV and Anime pages.
     */
    suspend fun getHomeCategories(): List<Category> = coroutineScope {
        try {
            val trendingMovies = async { tmdbService.getTrendingMovies().results.map { it.toMovie(MovieType.MOVIE) } }
            val popularMovies = async { tmdbService.getPopularMovies().results.map { it.toMovie(MovieType.MOVIE) } }
            val popularTV = async { tmdbService.getPopularTVShows().results.map { it.toMovie(MovieType.TV_SHOW) } }
            val trendingAnime = async { getTrendingAnime() }

            val stremioCatalogs = async {
                addonManager.getCatalogs().map { (name, metas) ->
                    Category(name, metas.map { it.toMovie() })
                }
            }

            val categories = mutableListOf(
                Category("Trending Movies", trendingMovies.await()),
                Category(TOP10_LABEL, trendingMovies.await().take(10)),
                Category("Popular TV Shows", popularTV.await()),
                Category("Trending Anime", trendingAnime.await()),
                Category("Popular Movies", popularMovies.await()),
            )
            categories.addAll(stremioCatalogs.await())
            categories
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("MovieRepository", "Error fetching from TMDB", e)
            emptyList()
        }
    }

    /** One shelf on the Movies or TV page. */
    suspend fun browse(kind: BrowseKind, list: BrowseList): List<Movie> {
        val type = if (kind == BrowseKind.MOVIES) MovieType.MOVIE else MovieType.TV_SHOW
        val response = when (kind) {
            BrowseKind.MOVIES -> when (list) {
                BrowseList.TRENDING -> tmdbService.getTrendingMovies()
                BrowseList.POPULAR -> tmdbService.getPopularMovies()
                BrowseList.TOP_RATED -> tmdbService.getTopRatedMovies()
                BrowseList.FRESH -> tmdbService.getNowPlayingMovies()
                BrowseList.SOON -> tmdbService.getUpcomingMovies()
            }
            BrowseKind.TV -> when (list) {
                BrowseList.TRENDING -> tmdbService.getTrendingTVShows()
                BrowseList.POPULAR -> tmdbService.getPopularTVShows()
                BrowseList.TOP_RATED -> tmdbService.getTopRatedTVShows()
                BrowseList.FRESH -> tmdbService.getOnTheAirTVShows()
                BrowseList.SOON -> tmdbService.getAiringTodayTVShows()
            }
        }
        return response.results.map { it.toMovie(type) }.filter { it.posterUrl.isNotBlank() }
    }

    /**
     * The Top 100 Halloween Movies: horror films tagged "halloween" on TMDB with a real number of votes
     * (about 240 qualify), ranked by weighted rating so obscure titles with a handful of perfect scores
     * do not outrank classics. Fetched in parallel and not cached here - the caller keeps it.
     */
    suspend fun halloweenTop100(): List<Movie> = coroutineScope {
        val pages = (1..HALLOWEEN_PAGES).map { page ->
            async {
                runCatching {
                    tmdbService.discoverMoviesFiltered(
                        withGenres = "27", withKeywords = HALLOWEEN_KEYWORD_ID.toString(),
                        sortBy = "vote_count.desc", voteCountGte = 250, page = page
                    ).results
                }.getOrDefault(emptyList())
            }
        }
        rankHalloween(pages.awaitAll().flatten()).map { it.toMovie(MovieType.MOVIE) }
    }

    /** One filtered list for the Movies or TV page: any mix of genre or mood, decade, studio or network. */
    suspend fun browseFiltered(kind: BrowseKind, filter: BrowseFilter): List<Movie> {
        val sort = when (filter.sort) {
            BrowseSort.POPULAR -> "popularity.desc"
            BrowseSort.TOP_RATED -> "vote_average.desc"
            BrowseSort.NEWEST -> if (kind == BrowseKind.MOVIES) "primary_release_date.desc" else "first_air_date.desc"
        }
        // Top rated needs a vote floor or it fills with titles rated 10 by three people; Newest must not
        // list films that have not come out.
        val minVotes = when (filter.sort) {
            BrowseSort.TOP_RATED -> if (kind == BrowseKind.MOVIES) 500 else 200
            BrowseSort.NEWEST -> 15
            BrowseSort.POPULAR -> if (kind == BrowseKind.MOVIES) 100 else 50
        }
        val today = java.time.LocalDate.now().toString()
        val from = filter.decade?.let { "${it.startYear}-01-01" }
        val toDate = (filter.decade?.let { "${it.endYear}-12-31" })
            ?: if (filter.sort == BrowseSort.NEWEST) today else null
        val to = toDate?.let { if (it > today) today else it }
        val results = if (kind == BrowseKind.MOVIES) {
            tmdbService.discoverMoviesFiltered(
                withGenres = filter.genreExpr, withCompanies = filter.provider?.id?.toString(),
                withWatchProviders = filter.streamer?.id?.toString(), watchRegion = filter.streamer?.let { "US" },
                releaseGte = from, releaseLte = to, sortBy = sort, voteCountGte = minVotes
            ).results.map { it.toMovie(MovieType.MOVIE) }
        } else {
            tmdbService.discoverTvFiltered(
                withGenres = filter.genreExpr, withNetworks = filter.provider?.id?.toString(),
                withWatchProviders = filter.streamer?.id?.toString(), watchRegion = filter.streamer?.let { "US" },
                airedGte = from, airedLte = to, sortBy = sort, voteCountGte = minVotes
            ).results.map { it.toMovie(MovieType.TV_SHOW) }
        }
        return results.filter { it.posterUrl.isNotBlank() }
    }

    /** The TV Guide: shows with an episode airing on [date], most popular first. */
    suspend fun tvGuide(date: java.time.LocalDate): List<Movie> {
        val day = date.toString()
        val zone = java.time.ZoneId.systemDefault().id
        return tmdbService.discoverTvAiring(airGte = day, airLte = day, timezone = zone)
            .results.map { it.toMovie(MovieType.TV_SHOW) }.filter { it.posterUrl.isNotBlank() }
    }

    /** A genre shelf: TMDB discover, most popular first. */
    suspend fun browseGenre(kind: BrowseKind, genreId: Int): List<Movie> = when (kind) {
        BrowseKind.MOVIES -> tmdbService.discoverMovies(withGenres = genreId.toString())
            .results.map { it.toMovie(MovieType.MOVIE) }
        BrowseKind.TV -> tmdbService.discoverTV(withGenres = genreId.toString(), voteCountGte = 100)
            .results.map { it.toMovie(MovieType.TV_SHOW) }
    }.filter { it.posterUrl.isNotBlank() }

    /** Bundled anime addons (One Pace) as shelves, for the Anime page. */
    suspend fun getAnimeAddonCategories(): List<Category> = runCatching {
        addonManager.getAnimeCatalogs().map { (name, metas) ->
            Category(name, metas.map { it.toMovie(forceType = MovieType.TV_SHOW) })
        }.filter { it.movies.isNotEmpty() }
    }.getOrDefault(emptyList())

    suspend fun search(query: String): List<Movie> = try {
        tmdbService.searchMulti(query).results.map { it.toMovie() }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.e("MovieRepository", "Search error", e)
        emptyList()
    }

    suspend fun getTrendingAnime(): List<Movie> = try {
        tmdbService.discoverTV(
            withGenres = "16",
            withOriginalLanguage = "ja",
            sortBy = "popularity.desc"
        ).results.map { it.toMovie(MovieType.TV_SHOW) }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w(TAG, "MovieRepository failed", e)
        emptyList()
    }

    suspend fun getMovieDetails(movie: Movie): Movie {
        if (movie.source == "stremio") {
            return try {
                val stremioType = if (movie.type == MovieType.MOVIE) "movie" else "series"
                val meta = withTimeoutOrNull(12_000) { addonManager.getMeta(stremioType, movie.id) }
                    ?: return movie
                val fallbackStill = meta.background ?: meta.poster
                val seasons = meta.videos.orEmpty()
                    .groupBy { it.season ?: 1 }
                    .toSortedMap()
                    .map { (seasonNo, vids) ->
                        val sorted = vids.sortedBy { it.episode ?: 0 }
                        val arc = sorted.firstOrNull()?.id?.substringBefore('_')
                            ?.let { ONE_PACE_ARCS[it] }
                        Season(
                            seasonNumber = seasonNo,
                            name = arc,
                            episodes = sorted.mapIndexed { i, v ->
                                Episode(
                                    id = v.id,
                                    name = v.title ?: v.name ?: "Episode ${v.episode ?: i + 1}",
                                    overview = v.overview,
                                    episodeNumber = v.episode ?: (i + 1),
                                    seasonNumber = seasonNo,
                                    stillPath = v.thumbnail ?: fallbackStill
                                )
                            }
                        )
                    }
                movie.copy(
                    title = meta.name,
                    posterUrl = meta.poster ?: movie.posterUrl,
                    backdropUrl = meta.background ?: movie.backdropUrl,
                    synopsis = meta.description ?: movie.synopsis,
                    seasons = seasons
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("MovieRepository", "Stremio meta error", e)
                movie
            }
        }

        if (movie.source != "tmdb") {
            val scraper = scrapers.find { it.sourceId == movie.source }
                ?: scrapers.find { it.name.lowercase().contains(movie.source.lowercase()) }
                ?: return movie
            return try {
                withTimeoutOrNull(10000) {
                    scraper.getMovieDetails(movie)
                } ?: movie
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "MovieRepository.getMovieDetails() failed", e)
                movie
            }
        }

        return try {
            val id = movie.id.toIntOrNull() ?: return movie
            if (movie.type == MovieType.MOVIE) {
                val tmdbMovie = tmdbService.getMovieDetails(id)
                tmdbMovie.toMovie(movie.type)
            } else {
                val tmdbMovie = tmdbService.getTVShowDetails(id)
                val seasons = tmdbMovie.seasons?.map { season ->
                    try {
                        tmdbService.getTVSeasonDetails(id, season.seasonNumber).toSeason()
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "MovieRepository.getMovieDetails() failed", e)
                        season.toSeason()
                    }
                } ?: emptyList()
                tmdbMovie.toMovie(movie.type).copy(seasons = seasons)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("MovieRepository", "Error getting movie details", e)
            movie
        }
    }

    fun getStreamSources(movie: Movie, episode: Episode? = null): Flow<StreamSource> = channelFlow {
        val enabledScrapers = getEnabledScrapers()

        // Several scrapers need the IMDb id (Stremio addons) or match better with it.
        // Resolve once and hand every scraper a Movie that carries it.
        val imdbId = if (movie.source == "tmdb") getImdbId(movie) else movie.id.takeIf { it.startsWith("tt") }
        val target = if (imdbId != null) movie.copy(imdbId = imdbId) else movie

        supervisorScope {
            // Scraper sources
            enabledScrapers.forEach { scraper ->
                launch {
                    try {
                        val sources = withTimeoutOrNull(SCRAPER_TIMEOUT_MS) {
                            if (target.type == MovieType.TV_SHOW && episode != null) {
                                scraper.getStreamSources(target, episode)
                            } else {
                                scraper.getStreamSources(target)
                            }
                        }
                        when {
                            sources == null -> Log.w("MovieRepository", "Scraper timed out: ${scraper.name}")
                            sources.isEmpty() -> Log.i("MovieRepository", "Scraper ${scraper.name}: 0 sources")
                            else -> {
                                Log.i("MovieRepository", "Scraper ${scraper.name}: ${sources.size} sources")
                                sources.forEach { send(it.copy(sourceId = scraper.sourceId)) }
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("MovieRepository", "Scraper error: ${scraper.name}", e)
                    }
                }
            }

            // Stremio addon sources
            launch {
                try {
                    val stremioType = if (movie.type == MovieType.MOVIE) "movie" else "series"
                    // Addon-native id (e.g. One Pace uses "RO_1" per episode, "pp_onepace" per series).
                    val nativeId: String? = when {
                        movie.source != "stremio" -> null
                        movie.type == MovieType.TV_SHOW && episode != null -> episode.id
                        else -> movie.id
                    }
                    // IMDb-keyed id for the big torrent addons (Torrentio etc).
                    val imdbStremioId: String? = imdbId?.takeIf { it.startsWith("tt") }?.let { tt ->
                        if (movie.type == MovieType.TV_SHOW && episode != null)
                            "$tt:${episode.seasonNumber}:${episode.episodeNumber}"
                        else tt
                    }

                    listOfNotNull(nativeId, imdbStremioId).distinct().forEach { sid ->
                        withTimeoutOrNull(SCRAPER_TIMEOUT_MS) {
                            addonManager.getStreams(stremioType, sid).forEach { stremioStream ->
                                stremioStream.toStreamSource()?.let { send(it) }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("MovieRepository", "Stremio error", e)
                }
            }
        }
    }

    /**
     * Resolves a TMDB id to an IMDb id, or returns the id unchanged if it already is one.
     *
     * Public because subtitle lookup needs it too: Stremio subtitle addons key on IMDb
     * ids (OpenSubtitles v3 declares idPrefixes ["tt"]), and a Movie that came from TMDB
     * carries a numeric id and a null imdbId until something resolves it.
     */
    suspend fun getImdbId(movie: Movie): String? {
        return try {
            if (movie.id.startsWith("tt")) return movie.id
            val id = movie.id.toIntOrNull() ?: return null
            if (movie.type == MovieType.MOVIE) {
                tmdbService.getMovieDetails(id).imdbId
            } else {
                tmdbService.getTVShowDetails(id).externalIds?.imdbId
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "MovieRepository.getImdbId() failed", e)
            null
        }
    }

    private fun StremioMeta.toMovie(forceType: MovieType? = null): Movie {
        return Movie(
            id = id,
            title = name,
            posterUrl = poster ?: "",
            backdropUrl = background,
            synopsis = description,
            year = releaseInfo?.take(4)?.toIntOrNull(),
            rating = imdbRating?.toDoubleOrNull(),
            type = forceType ?: if (type == "movie") MovieType.MOVIE else MovieType.TV_SHOW,
            source = "stremio"
        )
    }

    private fun TMDBMovie.toMovie(defaultType: MovieType? = null): Movie {
        val isMovie = mediaType == "movie" || (defaultType == MovieType.MOVIE) || (title != null && name == null)
        return Movie(
            id = id.toString(),
            title = title ?: name ?: "Unknown",
            posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" } ?: "",
            backdropUrl = backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
                ?: posterPath?.let { "https://image.tmdb.org/t/p/w780$it" },
            synopsis = overview,
            year = (releaseDate ?: firstAirDate)?.take(4)?.toIntOrNull(),
            rating = voteAverage,
            type = if (isMovie) MovieType.MOVIE else MovieType.TV_SHOW,
            source = "tmdb",
            trailerKey = videos.bestTrailerKey()
        )
    }

    private fun TMDBSeason.toSeason(): Season {
        return Season(
            seasonNumber = seasonNumber,
            episodes = episodes?.map { it.toEpisode() } ?: emptyList()
        )
    }

    private fun TMDBEpisode.toEpisode(): Episode {
        return Episode(
            id = id.toString(),
            name = name,
            overview = overview,
            episodeNumber = episodeNumber,
            seasonNumber = seasonNumber,
            stillPath = stillPath?.let { "https://image.tmdb.org/t/p/w500$it" }
        )
    }
}

private const val TAG = "MovieRepository"
/** TMDB keyword id for "halloween", and how many pages of it to read (20 films a page). */
private const val HALLOWEEN_KEYWORD_ID = 3335
private const val HALLOWEEN_PAGES = 12

/**
 * Orders candidate Halloween films best-first by weighted rating and keeps the top 100. Pure, so it
 * can be tested; drops duplicates and films with no poster.
 */
internal fun rankHalloween(candidates: List<com.ominix.vidiio.data.model.tmdb.TMDBMovie>): List<com.ominix.vidiio.data.model.tmdb.TMDBMovie> =
    candidates
        .distinctBy { it.id }
        .filter { !it.posterPath.isNullOrBlank() }
        .sortedByDescending { weightedRating(it.voteAverage ?: 0.0, it.voteCount ?: 0) }
        .take(100)

/** Which of the dedicated browse pages a request is for. */
enum class BrowseKind { MOVIES, TV }

/** The standard shelves every browse page has. What FRESH and SOON mean depends on the page. */
enum class BrowseList {
    TRENDING, POPULAR, TOP_RATED,

    /** Movies: in theatres now. TV: currently on the air. */
    FRESH,

    /** Movies: coming soon. TV: airing today. */
    SOON,
}
