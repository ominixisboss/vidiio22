package com.ominix.vidiio.data.anime

import com.ominix.vidiio.data.http.awaitResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Thin client for AniList's public GraphQL API (no key needed, ~90 requests a minute).
 *
 * Chosen over TMDB for this page because it has what an anime storefront is built on: real
 * seasons, per-episode air times, trending and popularity rankings, and banner art.
 */
class AniListService(
    private val client: OkHttpClient,
    private val moshi: Moshi = Moshi.Builder().build(),
) {
    private val responseAdapter = moshi.adapter(AniResponse::class.java)
    private val requestAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    /** Discovery lists: anything filterable by season, status, genre, and sortable. */
    suspend fun media(
        sort: String,
        perPage: Int = 20,
        season: String? = null,
        seasonYear: Int? = null,
        status: String? = null,
        genre: String? = null,
        minPopularity: Int? = null,
        page: Int = 1,
    ): List<AniMedia> {
        val variables = buildMap<String, Any?> {
            put("page", page)
            put("perPage", perPage)
            put("sort", listOf(sort))
            season?.let { put("season", it) }
            seasonYear?.let { put("seasonYear", it) }
            status?.let { put("status", it) }
            genre?.let { put("genre", it) }
            minPopularity?.let { put("minPopularity", it) }
        }
        val items = post(MEDIA_QUERY, variables).media
        return items.filter { it.isShowable() }
    }

    /** Episodes airing between [from] and [to] (unix seconds). Returns the page and whether more follow. */
    suspend fun schedule(
        from: Long,
        to: Long,
        descending: Boolean,
        page: Int = 1,
        perPage: Int = 50,
    ): Pair<List<AniScheduleEntry>, Boolean> {
        val variables = mapOf<String, Any?>(
            "page" to page,
            "perPage" to perPage,
            "from" to from,
            "to" to to,
            "sort" to listOf(if (descending) "TIME_DESC" else "TIME"),
        )
        val result = post(SCHEDULE_QUERY, variables)
        return result.airingSchedules.filter { it.media.isShowable() } to (result.pageInfo?.hasNextPage == true)
    }

    private suspend fun post(query: String, variables: Map<String, Any?>): AniPage {
        val body = requestAdapter.toJson(mapOf("query" to query, "variables" to variables))

        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Accept", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).awaitResponse().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("AniList HTTP ${response.code}")
            val parsed = responseAdapter.fromJson(text) ?: throw IOException("AniList: empty response")
            parsed.errors?.firstOrNull()?.message?.let { throw IOException("AniList: $it") }
            return parsed.data?.page ?: AniPage()
        }
    }

    companion object {
        private const val ENDPOINT = "https://graphql.anilist.co"

        /** Not shown: adult titles and formats that are not a watchable show or film. */
        private val SHOWN_FORMATS = setOf("TV", "TV_SHORT", "MOVIE", "ONA", "OVA", "SPECIAL")

        internal fun AniMedia.isShowable(): Boolean =
            !isAdult && id != 0 && (format == null || format in SHOWN_FORMATS)

        private const val MEDIA_FIELDS = """
            id idMal
            title { romaji english native }
            coverImage { extraLarge large color }
            bannerImage
            description(asHtml: false)
            format status episodes duration season seasonYear
            averageScore popularity trending genres
            studios(isMain: true) { nodes { name } }
            nextAiringEpisode { episode airingAt }
            startDate { year }
            trailer { id site }
            isAdult
        """

        private const val MEDIA_QUERY = """
            query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}sort: [MediaSort], ${'$'}season: MediaSeason,
                   ${'$'}seasonYear: Int, ${'$'}status: MediaStatus, ${'$'}genre: String, ${'$'}minPopularity: Int) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                pageInfo { hasNextPage }
                media(type: ANIME, isAdult: false, sort: ${'$'}sort, season: ${'$'}season, seasonYear: ${'$'}seasonYear,
                      status: ${'$'}status, genre: ${'$'}genre, popularity_greater: ${'$'}minPopularity,
                      format_in: [TV, TV_SHORT, MOVIE, ONA]) {
                  $MEDIA_FIELDS
                }
              }
            }
        """

        private const val SCHEDULE_QUERY = """
            query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}from: Int, ${'$'}to: Int, ${'$'}sort: [AiringSort]) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                pageInfo { hasNextPage }
                airingSchedules(airingAt_greater: ${'$'}from, airingAt_lesser: ${'$'}to, sort: ${'$'}sort) {
                  episode airingAt
                  media {
                    $MEDIA_FIELDS
                  }
                }
              }
            }
        """
    }
}
