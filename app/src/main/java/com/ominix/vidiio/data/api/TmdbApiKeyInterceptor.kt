package com.ominix.vidiio.data.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Appends `api_key` to TMDB requests.
 *
 * The key used to be a default argument repeated on all eleven [TMDBService] methods, so
 * rotating it meant eleven edits and it was impossible to tell from a call site whether
 * the key was being sent at all. Here it is applied in exactly one place.
 *
 * The host check is not decoration: the same OkHttp client is shared with the Stremio
 * addon calls and, historically, the scrapers. Appending the key unconditionally would
 * hand it to every third-party host the app talks to.
 */
class TmdbApiKeyInterceptor(private val apiKey: String) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != TMDB_HOST) return chain.proceed(request)

        // Don't stack a second api_key if a caller already set one.
        if (request.url.queryParameter(QUERY_PARAM) != null) return chain.proceed(request)

        val url = request.url.newBuilder()
            .addQueryParameter(QUERY_PARAM, apiKey)
            .build()
        return chain.proceed(request.newBuilder().url(url).build())
    }

    private companion object {
        const val TMDB_HOST = "api.themoviedb.org"
        const val QUERY_PARAM = "api_key"
    }
}
