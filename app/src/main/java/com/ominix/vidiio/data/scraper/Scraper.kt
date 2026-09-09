package com.ominix.vidiio.data.scraper

import com.ominix.vidiio.data.model.Category
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.Episode
import com.ominix.vidiio.data.model.StreamSource

interface Scraper {
    val name: String
    val sourceId: String
    val baseUrl: String

    suspend fun getHomeCategories(): List<Category>
    suspend fun search(query: String): List<Movie>
    suspend fun getMovieDetails(movie: Movie): Movie
    suspend fun getStreamSources(movie: Movie, episode: Episode? = null): List<StreamSource>
}
