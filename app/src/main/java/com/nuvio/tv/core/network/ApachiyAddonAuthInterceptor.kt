package com.nuvio.tv.core.network

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.auth.AuthManager
import io.github.jan.supabase.auth.Auth
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

class ApachiyAddonAuthInterceptor(
    private val auth: Auth,
    private val authManager: AuthManager,
    private val apachiyApiHost: String = apachiyHostFromBaseUrl(BuildConfig.APACHIY_API_BASE_URL)
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!shouldAttachAuth(request.url.host)) {
            return chain.proceed(request)
        }

        val initialRequest = withAccessToken(request, auth.currentAccessTokenOrNull())
        val response = chain.proceed(initialRequest)
        if (response.code != 401) {
            return response
        }

        response.close()
        val refreshed = runBlocking {
            authManager.refreshCurrentSessionSerialized("Apachiy addon 401")
        }
        if (!refreshed) {
            return chain.proceed(initialRequest)
        }

        val retryRequest = withAccessToken(request, auth.currentAccessTokenOrNull())
        return chain.proceed(retryRequest)
    }

    private fun shouldAttachAuth(host: String): Boolean {
        if (apachiyApiHost.isBlank()) return false
        return host.equals(apachiyApiHost, ignoreCase = true)
    }

    private fun withAccessToken(request: Request, token: String?): Request {
        if (token.isNullOrBlank()) return request
        return request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
    }

    companion object {
        fun apachiyHostFromBaseUrl(baseUrl: String): String {
            val trimmed = baseUrl.trim().trimEnd('/')
            if (trimmed.isBlank()) return ""
            return trimmed.toHttpUrlOrNull()?.host.orEmpty()
        }
    }
}
