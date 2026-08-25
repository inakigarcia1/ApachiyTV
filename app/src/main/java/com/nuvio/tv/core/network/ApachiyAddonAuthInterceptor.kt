package com.nuvio.tv.core.network

import android.util.Log
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.auth.AuthManager
import io.github.jan.supabase.auth.Auth
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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

        val hasToken = !auth.currentAccessTokenOrNull().isNullOrBlank()
        val initialRequest = withAccessToken(request, auth.currentAccessTokenOrNull())
        val response = try {
            chain.proceed(initialRequest)
        } catch (e: Exception) {
            // #region agent log
            agentDebugLog(
                hypothesisId = "H6",
                location = "ApachiyAddonAuthInterceptor.intercept",
                message = "apachiy-hosted request failed",
                data = mapOf(
                    "host" to request.url.host,
                    "path" to request.url.encodedPath,
                    "hasToken" to hasToken,
                    "error" to (e.javaClass.simpleName + ": " + (e.message?.take(120) ?: "")),
                    "matchedHost" to true
                )
            )
            // #endregion
            throw e
        }
        // #region agent log
        agentDebugLog(
            hypothesisId = if (request.url.encodedPath.contains("/img")) "H6" else "H2",
            location = "ApachiyAddonAuthInterceptor.intercept",
            message = "apachiy-hosted request",
            data = mapOf(
                "host" to request.url.host,
                "path" to request.url.encodedPath,
                "hasToken" to hasToken,
                "status" to response.code,
                "matchedHost" to true,
                "isImg" to request.url.encodedPath.contains("/img")
            )
        )
        // #endregion
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
        private const val TAG = "ApachiyAddonAuth"

        fun apachiyHostFromBaseUrl(baseUrl: String): String {
            val trimmed = baseUrl.trim().trimEnd('/')
            if (trimmed.isBlank()) return ""
            return trimmed.toHttpUrlOrNull()?.host.orEmpty()
        }

        // #region agent log
        private val debugClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(500, TimeUnit.MILLISECONDS)
                .readTimeout(500, TimeUnit.MILLISECONDS)
                .build()
        }

        fun agentDebugLog(
            hypothesisId: String,
            location: String,
            message: String,
            data: Map<String, Any?>
        ) {
            try {
                val payload = JSONObject()
                    .put("sessionId", "ba4e3b")
                    .put("runId", "post-fix")
                    .put("hypothesisId", hypothesisId)
                    .put("location", location)
                    .put("message", message)
                    .put("data", JSONObject(data))
                    .put("timestamp", System.currentTimeMillis())
                    .toString()
                Log.d(TAG, payload)
                val body = payload.toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url("http://10.0.2.2:7748/ingest/47477325-c05a-4e29-9039-228e044dc406")
                    .header("X-Debug-Session-Id", "ba4e3b")
                    .post(body)
                    .build()
                debugClient.newCall(req).enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: java.io.IOException) = Unit
                    override fun onResponse(call: okhttp3.Call, response: Response) {
                        response.close()
                    }
                })
            } catch (_: Exception) {
            }
        }
        // #endregion
    }
}
