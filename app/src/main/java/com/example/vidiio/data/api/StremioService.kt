package com.example.vidiio.data.api

import com.example.vidiio.data.model.stremio.CatalogResponse
import com.example.vidiio.data.model.stremio.Manifest
import com.example.vidiio.data.model.stremio.MetaResponse
import com.example.vidiio.data.model.stremio.StreamResponse
import retrofit2.http.GET
import retrofit2.http.Url

interface StremioService {
    @GET
    suspend fun getManifest(@Url url: String): Manifest

    @GET
    suspend fun getStreams(@Url url: String): StreamResponse

    @GET
    suspend fun getCatalog(@Url url: String): CatalogResponse

    @GET
    suspend fun getMeta(@Url url: String): MetaResponse
}
