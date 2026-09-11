package com.nuvio.tv.data.remote.api

import com.nuvio.tv.data.remote.dto.AddonManifestDto
import com.nuvio.tv.data.remote.dto.CatalogResponseDto
import com.nuvio.tv.data.remote.dto.MetaResponseDto
import com.nuvio.tv.data.remote.dto.StreamResponseDto
import com.nuvio.tv.data.remote.dto.SubtitleResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url
import okhttp3.RequestBody

interface AddonApi {

    @GET
    suspend fun getManifest(@Url manifestUrl: String): Response<AddonManifestDto>

    @GET
    suspend fun getCatalog(@Url catalogUrl: String): Response<CatalogResponseDto>

    @GET
    suspend fun getMeta(@Url metaUrl: String): Response<MetaResponseDto>

    @GET
    suspend fun getStreams(@Url streamUrl: String): Response<StreamResponseDto>

    @GET
    suspend fun getSubtitles(@Url subtitleUrl: String): Response<SubtitleResponseDto>

    @POST
    suspend fun postSubtitles(
        @Url subtitleUrl: String,
        @Body body: RequestBody,
    ): Response<SubtitleResponseDto>
}
