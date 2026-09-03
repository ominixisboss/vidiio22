package com.example.vidiio.data.scraper

import com.example.vidiio.data.model.Category
import com.example.vidiio.data.model.Movie
import com.example.vidiio.data.model.Episode
import com.example.vidiio.data.model.StreamSource

interface Scraper {
    val name: String
    val sourceId: String
    val baseUrl: String

    suspend fun getHomeCategories(): List<Category>
    suspend fun search(query: String): List<Movie>
    suspend fun getMovieDetails(movie: Movie): Movie
    suspend fun getStreamSources(movie: Movie, episode: Episode? = null): List<StreamSource>
}
