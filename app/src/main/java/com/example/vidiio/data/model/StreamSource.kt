package com.example.vidiio.data.model

import kotlinx.serialization.Serializable

@Serializable
data class StreamSource(
    val serverName: String,
    val url: String,
    val sourceName: String = "",
    val sourceId: String = "",
    val isM3u8: Boolean = true,
    val quality: String? = null,
    val size: String? = null,
    val seeders: Int? = null,
    val headers: Map<String, String>? = null
)
