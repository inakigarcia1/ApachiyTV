package com.nuvio.tv.core.network

import com.nuvio.tv.core.auth.AuthManager
import io.github.jan.supabase.auth.Auth
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApachiyAddonAuthInterceptorTest {

    @Test
    fun `adds bearer token only for matching apachiy host`() {
        val auth = mockk<Auth>()
        every { auth.currentAccessTokenOrNull() } returns "access-token"
        val authManager = mockk<AuthManager>(relaxed = true)

        val interceptor = ApachiyAddonAuthInterceptor(
            auth = auth,
            authManager = authManager,
            apachiyApiHost = "api.apachiy.test"
        )

        var capturedAuth: String? = null
        val recordingInterceptor = Interceptor { chain ->
            capturedAuth = chain.request().header("Authorization")
            successResponse(chain.request())
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor(recordingInterceptor)
            .build()

        client.newCall(
            Request.Builder().url("https://api.apachiy.test/addons/manifest.json").build()
        ).execute().close()

        assertEquals("Bearer access-token", capturedAuth)
    }

    @Test
    fun `does not add bearer token for external host`() {
        val auth = mockk<Auth>()
        every { auth.currentAccessTokenOrNull() } returns "access-token"
        val authManager = mockk<AuthManager>(relaxed = true)

        val interceptor = ApachiyAddonAuthInterceptor(
            auth = auth,
            authManager = authManager,
            apachiyApiHost = "api.apachiy.test"
        )

        var capturedAuth: String? = null
        val recordingInterceptor = Interceptor { chain ->
            capturedAuth = chain.request().header("Authorization")
            successResponse(chain.request())
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor(recordingInterceptor)
            .build()

        client.newCall(
            Request.Builder().url("https://v3-cinemeta.strem.io/manifest.json").build()
        ).execute().close()

        assertNull(capturedAuth)
    }

    @Test
    fun `retries once with refreshed token on 401`() {
        val auth = mockk<Auth>()
        every { auth.currentAccessTokenOrNull() } returnsMany listOf("stale-token", "fresh-token")
        val authManager = mockk<AuthManager>()
        coEvery { authManager.refreshCurrentSessionSerialized("Apachiy addon 401") } returns true

        val interceptor = ApachiyAddonAuthInterceptor(
            auth = auth,
            authManager = authManager,
            apachiyApiHost = "api.apachiy.test"
        )

        var attempt = 0
        val serverInterceptor = Interceptor { chain ->
            attempt++
            val authHeader = chain.request().header("Authorization")
            if (attempt == 1) {
                assertEquals("Bearer stale-token", authHeader)
                unauthorizedResponse(chain.request())
            } else {
                assertEquals("Bearer fresh-token", authHeader)
                successResponse(chain.request())
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor(serverInterceptor)
            .build()

        val response = client.newCall(
            Request.Builder().url("https://api.apachiy.test/addons/meta/movie/tt123.json").build()
        ).execute()
        response.close()

        assertEquals(200, response.code)
        assertEquals(2, attempt)
    }

    private fun successResponse(request: Request): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("".toResponseBody(null))
            .build()

    private fun unauthorizedResponse(request: Request): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body("".toResponseBody(null))
            .build()
}
