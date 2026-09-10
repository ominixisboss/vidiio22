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
        @Query("languages") languages: String = "EN",
        @Query("type") type: String? = null, // movie or tv
        @Query("season_number") seasonNumber: Int? = null,
        @Query("episode_number") episodeNumber: Int? = null,
        // The top-level `url` is a .zip, which ExoPlayer cannot read. unpack=1 adds
        // unpack_files[], the individual .srt files - the only playable form SubDL offers.
        @Query("unpack") unpack: Int = 1,
        @Query("subs_per_page") subsPerPage: Int = 30,
        // SubDL asks integrations to identify themselves.
        @Query("client") client: String = "custom_integration"
    ): SubdlResponse
}
