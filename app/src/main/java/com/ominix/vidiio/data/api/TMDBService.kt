package com.ominix.vidiio.data.api

import com.ominix.vidiio.data.model.tmdb.TMDBMovie
import com.ominix.vidiio.data.model.tmdb.TMDBResponse
import com.ominix.vidiio.data.model.tmdb.TMDBSeason
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * TMDB endpoints.
 *
 * `api_key` is NOT a parameter here - [TmdbApiKeyInterceptor] adds it to every request to
 * api.themoviedb.org. It was previously a default argument duplicated on all eleven
 * methods.
 */
interface TMDBService {

    @GET("trending/movie/day")
    suspend fun getTrendingMovies(): TMDBResponse<TMDBMovie>

    @GET("trending/tv/day")
    suspend fun getTrendingTVShows(): TMDBResponse<TMDBMovie>

    @GET("movie/popular")
    suspend fun getPopularMovies(): TMDBResponse<TMDBMovie>

    @GET("tv/popular")
    suspend fun getPopularTVShows(): TMDBResponse<TMDBMovie>

    @GET("movie/top_rated")
    suspend fun getTopRatedMovies(): TMDBResponse<TMDBMovie>

    @GET("movie/now_playing")
    suspend fun getNowPlayingMovies(): TMDBResponse<TMDBMovie>

    @GET("movie/upcoming")
    suspend fun getUpcomingMovies(): TMDBResponse<TMDBMovie>

    @GET("tv/top_rated")
    suspend fun getTopRatedTVShows(): TMDBResponse<TMDBMovie>

    @GET("tv/on_the_air")
    suspend fun getOnTheAirTVShows(): TMDBResponse<TMDBMovie>

    @GET("tv/airing_today")
    suspend fun getAiringTodayTVShows(): TMDBResponse<TMDBMovie>

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String
    ): TMDBResponse<TMDBMovie>

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("append_to_response") appendToResponse: String = "videos"
    ): TMDBMovie

    @GET("tv/{tv_id}")
    suspend fun getTVShowDetails(
        @Path("tv_id") tvId: Int,
        @Query("append_to_response") appendToResponse: String = "external_ids,videos"
    ): TMDBMovie

    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getTVSeasonDetails(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int
    ): TMDBSeason

    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("with_genres") withGenres: String? = null,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("vote_count.gte") voteCountGte: Int = 200
    ): TMDBResponse<TMDBMovie>

    /** Discover with every filter the Movies page offers. Unset filters are simply not sent. */
    @GET("discover/movie")
    suspend fun discoverMoviesFiltered(
        @Query("with_genres") withGenres: String? = null,
        @Query("with_keywords") withKeywords: String? = null,
        @Query("with_companies") withCompanies: String? = null,
        @Query("with_watch_providers") withWatchProviders: String? = null,
        @Query("watch_region") watchRegion: String? = null,
        @Query("primary_release_date.gte") releaseGte: String? = null,
        @Query("primary_release_date.lte") releaseLte: String? = null,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("vote_count.gte") voteCountGte: Int? = 100,
        @Query("page") page: Int = 1
    ): TMDBResponse<TMDBMovie>

    /** The same for TV: networks instead of studios, first-air dates instead of release dates. */
    @GET("discover/tv")
    suspend fun discoverTvFiltered(
        @Query("with_genres") withGenres: String? = null,
        @Query("with_networks") withNetworks: String? = null,
        @Query("with_watch_providers") withWatchProviders: String? = null,
        @Query("watch_region") watchRegion: String? = null,
        @Query("first_air_date.gte") airedGte: String? = null,
        @Query("first_air_date.lte") airedLte: String? = null,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("vote_count.gte") voteCountGte: Int? = 50,
        @Query("page") page: Int = 1
    ): TMDBResponse<TMDBMovie>

    /** Shows with an episode airing in a date range: the TV Guide. */
    @GET("discover/tv")
    suspend fun discoverTvAiring(
        @Query("air_date.gte") airGte: String,
        @Query("air_date.lte") airLte: String,
        @Query("timezone") timezone: String,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("with_original_language") language: String? = "en",
        @Query("vote_count.gte") voteCountGte: Int? = 0,
        @Query("page") page: Int = 1
    ): TMDBResponse<TMDBMovie>

    @GET("discover/tv")
    suspend fun discoverTV(
        @Query("with_genres") withGenres: String? = null,
        @Query("with_original_language") withOriginalLanguage: String? = null,
        @Query("sort_by") sortBy: String? = "popularity.desc",
        @Query("vote_count.gte") voteCountGte: Int? = null
    ): TMDBResponse<TMDBMovie>
}
