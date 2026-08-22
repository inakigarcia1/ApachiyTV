package com.nuvio.tv.data.remote.device

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.data.remote.device.dto.AccountProvisionRequest
import com.nuvio.tv.data.remote.device.dto.AccountProvisionResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * REST contract for the Apachiy .NET API's `POST /v1/account/provision`
 * endpoint. Links or creates a local Apachiy user for the Supabase TV session.
 */
interface ApachiyAccountApi {

    @POST("v1/account/provision")
    suspend fun provision(
        @Header("Authorization") bearer: String,
        @Header("X-Apachiy-Client") clientHeader: String = ApachiyDeviceApi.CLIENT_HEADER_VALUE,
        @Body body: AccountProvisionRequest
    ): Response<AccountProvisionResponse>
}
