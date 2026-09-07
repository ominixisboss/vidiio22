package com.example.vidiio.data.api

import com.example.vidiio.data.model.tmdb.TMDBMovie
import com.example.vidiio.data.model.tmdb.TMDBResponse
import com.example.vidiio.data.model.tmdb.TMDBSeason
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TMDBService {

    @GET("trending/movie/day")
    suspend fun getTrendingMovies(
        @Query("api_key") apiKey: String = "13385ad4858c3f8568ce182c7287d9a9"
    ): TMDBResponse<TMDBMovie>

    @GET("trending/tv/day")
    suspend fun getTrendingTVShows(
        @Query("api_key") apiKey: String = "13385ad4858c3f8568ce182c7287d9a9"
    ): TMDBResponse<TMDBMovie>

    @GET("movie/popular")
    suspend fun getPopularMovies(
        @Query("api_key") apiKey: String = "13385ad4858c3f8568ce182c7287d9a9"
    ): TMDBResponse<TMDBMovie>

    @GET("tv/popular")
    suspend fun getPopularTVShows(
        @Query("api_key") apiKey: String = "13385ad4858c3f8568ce182c7287d9a9"
    ): TMDBResponse<TMDBMovie>

    @GET("movie/top_rated")
    suspend fun getTopRatedMovies(
        @Query("api_key") apiKey: String = "13385ad4858c3f8568ce182c7287d9a9"
    ): TMDBResponse<TMDBMovie>

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("api_key") apiKey: String = "13385ad4858c3f8568ce182c7287d9a9"
    ): TMDBResponse<TMDBMovie>

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String = "13385ad4858c3f8568ce182c7287d9a9"
    ): TMDBMovie

    @GET("tv/{tv_id}")
    suspend fun getTVShowDetails(
        @Path("tv_id") tvId: Int,
        @Query("api_key") apiKey: String = "13385ad4858c3f8568ce182c7287d9a9",
        @Query("append_to_response") appendToResponse: String = "external_ids"
    ): TMDBMovie

    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getTVSeasonDetails(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int,
        @Query("api_key") apiKey: String = "13385ad4858c3f8568ce182c7287d9a9"
    ): TMDBSeason

    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("with_genres") withGenres: String? = null,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("vote_count.gte") voteCountGte: Int = 200,
        @Query("api_key") apiKey: String = "13385ad4858c3f8568ce182c7287d9a9"
    ): TMDBResponse<TMDBMovie>

    @GET("discover/tv")
    suspend fun discoverTV(
        @Query("with_genres") withGenres: String? = null,
        @Query("with_original_language") withOriginalLanguage: String? = null,
        @Query("sort_by") sortBy: String? = "popularity.desc",
        @Query("vote_count.gte") voteCountGte: Int? = null,
        @Query("api_key") apiKey: String = "13385ad4858c3f8568ce182c7287d9a9"
    ): TMDBResponse<TMDBMovie>
}
