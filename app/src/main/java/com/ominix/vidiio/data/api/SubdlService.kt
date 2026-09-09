package com.ominix.vidiio.data.api

import com.ominix.vidiio.data.model.subtitles.SubdlResponse
import com.ominix.vidiio.data.model.subtitles.SubdlSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface SubdlService {
    @GET("subtitles")
    suspend fun searchSubtitles(
        @Query("api_key") apiKey: String,
        @Query("tmdb_id") tmdbId: Int? = null,
        @Query("imdb_id") imdbId: String? = null,
        @Query("languages") languages: String = "en",
        @Query("type") type: String? = null // movie or tv
    ): SubdlResponse
}
