package com.example.vidiio.data.model

data class Category(
    val name: String,
    val movies: List<Movie>
)

/** Marker name for the numbered "Top 10" rail on the home screen. */
const val TOP10_LABEL = "Top 10 This Week"
