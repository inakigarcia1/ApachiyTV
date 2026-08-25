package com.nuvio.tv.data.remote.device

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.data.remote.device.dto.DeviceRegistrationRequest
import com.nuvio.tv.data.remote.device.dto.DeviceRegistrationResponse
import com.nuvio.tv.data.remote.device.dto.DeviceSummaryDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * REST contract for the Apachiy .NET API's `POST /v1/devices/register`
 * endpoint. Validated via Supabase JWT (HS256 with the JWT_SECRET env var
 * shared by the self-hosted Supabase Auth and the .NET API).
 *
 * Set `APACHIY_API_BASE_URL` in `local.properties` to your .NET API host.
 */
interface ApachiyDeviceApi {

    @POST("v1/devices/register")
    suspend fun registerDevice(
        @Header("Authorization") bearer: String,
        @Header("X-Apachiy-Client") clientHeader: String = CLIENT_HEADER_VALUE,
        @Body body: DeviceRegistrationRequest
    ): Response<DeviceRegistrationResponse>

    @GET("v1/devices")
    suspend fun listDevices(
        @Header("Authorization") bearer: String,
        @Header("X-Apachiy-Client") clientHeader: String = CLIENT_HEADER_VALUE
    ): Response<List<DeviceSummaryDto>>

    companion object {
        const val CLIENT_HEADER_VALUE: String = "apachiy-tv/${BuildConfig.VERSION_NAME}"
    }
}