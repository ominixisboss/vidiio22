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

class MovieRepository(
    private val tmdbService: TMDBService,
    private val scrapers: List<Scraper>,
    private val settingsRepository: SettingsRepository,
    private val addonManager: AddonManager
) {

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

        /** Home-screen genre shelves: label to TMDB genre id. */
        val GENRE_SHELVES = listOf(
            "Action" to 28,
            "Comedy" to 35,
            "Horror" to 27,
            "Sci-Fi" to 878,
            "Animation" to 16,
            "Thriller" to 53,
            "Romance" to 10749,
        )
    }

    private suspend fun getEnabledScrapers(): List<Scraper> {
        val enabledSources = settingsRepository.sourcesFlow.first()
        return scrapers.filter { it.sourceId in enabledSources }
    }

    suspend fun getHomeCategories(): List<Category> = coroutineScope {
        try {
            val trendingMovies = async { tmdbService.getTrendingMovies().results.map { it.toMovie(MovieType.MOVIE) } }
            val popularMovies = async { tmdbService.getPopularMovies().results.map { it.toMovie(MovieType.MOVIE) } }
            val popularTV = async { tmdbService.getPopularTVShows().results.map { it.toMovie(MovieType.TV_SHOW) } }
            val topRated = async { tmdbService.getTopRatedMovies().results.map { it.toMovie(MovieType.MOVIE) } }
            
            val popularAnime = async { getPopularAnime() }
            val trendingAnime = async { getTrendingAnime() }
            val topRatedAnime = async { getTopRatedAnime() }

            val stremioCatalogs = async {
                addonManager.getCatalogs().map { (name, metas) ->
                    Category(name, metas.map { it.toMovie() })
                }
            }

            // Bundled anime addons (e.g. One Pace) get their own row in the anime block.
            val animeAddonCatalogs = async {
                runCatching {
                    addonManager.getAnimeCatalogs().map { (name, metas) ->
                        Category(name, metas.map { it.toMovie(forceType = MovieType.TV_SHOW) })
                    }
                }.getOrDefault(emptyList())
            }

            val genreShelves = async {
                GENRE_SHELVES.map { (label, id) ->
                    async {
                        val movies = runCatching {
                            tmdbService.discoverMovies(withGenres = id.toString()).results.map { it.toMovie(MovieType.MOVIE) }
                        }.getOrDefault(emptyList())
                        Category(label, movies)
                    }
                }.map { it.await() }.filter { it.movies.isNotEmpty() }
            }

            val categories = mutableListOf(
                Category("Trending Movies", trendingMovies.await()),
                Category(TOP10_LABEL, trendingMovies.await().take(10)),
                Category("Trending Anime", trendingAnime.await()),
                Category("Popular Movies", popularMovies.await()),
                Category("Popular TV Shows", popularTV.await()),
                Category("Top Rated Movies", topRated.await()),
                Category("Popular Anime", popularAnime.await()),
                Category("Top Rated Anime", topRatedAnime.await())
            )

            categories.addAll(animeAddonCatalogs.await().filter { it.movies.isNotEmpty() })
            categories.addAll(genreShelves.await())
            categories.addAll(stremioCatalogs.await())
            categories
        } catch (e: Exception) {
            Log.e("MovieRepository", "Error fetching from TMDB", e)
            emptyList()
        }
    }

    suspend fun search(query: String): List<Movie> = try {
        tmdbService.searchMulti(query).results.map { it.toMovie() }
    } catch (e: Exception) {
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
        emptyList()
    }

    suspend fun getPopularAnime(): List<Movie> = try {
        tmdbService.discoverTV(
            withGenres = "16",
            withOriginalLanguage = "ja",
            sortBy = "popularity.desc"
        ).results.map { it.toMovie(MovieType.TV_SHOW) }
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun getTopRatedAnime(): List<Movie> = try {
        tmdbService.discoverTV(
            withGenres = "16",
            withOriginalLanguage = "ja",
            sortBy = "vote_average.desc",
            voteCountGte = 200
        ).results.map { it.toMovie(MovieType.TV_SHOW) }
    } catch (e: Exception) {
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
                        season.toSeason()
                    }
                } ?: emptyList()
                tmdbMovie.toMovie(movie.type).copy(seasons = seasons)
            }
        } catch (e: Exception) {
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
                    Log.e("MovieRepository", "Stremio error", e)
                }
            }
        }
    }

    private suspend fun getImdbId(movie: Movie): String? {
        return try {
            if (movie.id.startsWith("tt")) return movie.id
            val id = movie.id.toIntOrNull() ?: return null
            if (movie.type == MovieType.MOVIE) {
                tmdbService.getMovieDetails(id).imdbId
            } else {
                tmdbService.getTVShowDetails(id).externalIds?.imdbId
            }
        } catch (e: Exception) {
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
