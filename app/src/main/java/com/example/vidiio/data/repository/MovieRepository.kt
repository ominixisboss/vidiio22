package com.example.vidiio.data.repository

import com.example.vidiio.data.api.TMDBService
import com.example.vidiio.data.model.Category
import com.example.vidiio.data.model.TOP10_LABEL
import com.example.vidiio.data.model.Movie
import com.example.vidiio.data.model.MovieType
import com.example.vidiio.data.model.Season
import com.example.vidiio.data.model.Episode
import com.example.vidiio.data.model.StreamSource
import com.example.vidiio.data.model.stremio.StremioMeta
import com.example.vidiio.data.model.tmdb.TMDBMovie
import com.example.vidiio.data.model.tmdb.bestTrailerKey
import com.example.vidiio.data.model.tmdb.TMDBSeason
import com.example.vidiio.data.model.tmdb.TMDBEpisode
import com.example.vidiio.data.scraper.Scraper
import com.example.vidiio.data.stremio.AddonManager
import com.example.vidiio.data.stremio.toStreamSource
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

            // Stremio sources
            launch {
                try {
                    if (imdbId != null && imdbId.startsWith("tt")) {
                        val stremioId = if (movie.type == MovieType.TV_SHOW && episode != null) {
                            "$imdbId:${episode.seasonNumber}:${episode.episodeNumber}"
                        } else {
                            imdbId
                        }
                        
                        withTimeoutOrNull(SCRAPER_TIMEOUT_MS) {
                            addonManager.getStreams(
                                if (movie.type == MovieType.MOVIE) "movie" else "series",
                                stremioId
                            ).forEach { stremioStream ->
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

    private fun StremioMeta.toMovie(): Movie {
        return Movie(
            id = id,
            title = name,
            posterUrl = poster ?: "",
            synopsis = description,
            year = releaseInfo?.take(4)?.toIntOrNull(),
            rating = imdbRating?.toDoubleOrNull(),
            type = if (type == "movie") MovieType.MOVIE else MovieType.TV_SHOW,
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
