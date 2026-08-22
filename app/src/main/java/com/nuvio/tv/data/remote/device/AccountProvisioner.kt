package com.nuvio.tv.data.remote.device

import android.util.Log
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.auth.AuthManager
import com.nuvio.tv.data.remote.device.dto.AccountProvisionRequest
import com.nuvio.tv.data.remote.device.dto.AccountProvisionResponse
import io.github.jan.supabase.auth.Auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ApachiyAccountProvisioner"
private const val MAX_ATTEMPTS = 3
private const val RETRY_BASE_DELAY_MS = 5_000L
private const val MIN_RETRY_GAP_MS = 5_000L

/**
 * Links or creates the local Apachiy API user after a successful Supabase auth.
 * Non-blocking: failures are logged and surfaced as warnings; the TV session stays valid.
 */
@Singleton
class AccountProvisioner @Inject constructor(
    private val authManager: AuthManager,
    private val supabaseAuth: Auth,
    private val apachiyAccountApi: ApachiyAccountApi
) {
    private val mutex = Mutex()
    private var lastAttemptMs = 0L

    suspend fun provisionAfterAuth(password: String): ProvisionResult {
        if (BuildConfig.APACHIY_API_BASE_URL.trim().isBlank()) {
            Log.d(TAG, "APACHIY_API_BASE_URL empty; skipping account provision.")
            return ProvisionResult.Skipped
        }
        val accessToken = supabaseAuth.currentAccessTokenOrNull() ?: run {
            Log.w(TAG, "No access token after auth; cannot provision.")
            return ProvisionResult.NotAuthenticated
        }

        return mutex.withLock {
            val now = System.currentTimeMillis()
            if (now - lastAttemptMs < MIN_RETRY_GAP_MS) {
                return ProvisionResult.Success
            }
            lastAttemptMs = now
            attemptWithRetry(accessToken, password)
        }
    }

    private suspend fun attemptWithRetry(accessToken: String, password: String): ProvisionResult {
        var attempt = 0
        var lastError: String? = null
        var currentToken = accessToken
        while (attempt < MAX_ATTEMPTS) {
            attempt++
            try {
                val resp = apachiyAccountApi.provision(
                    bearer = "Bearer $currentToken",
                    body = AccountProvisionRequest(password = password)
                )
                if (resp.isSuccessful) {
                    val body: AccountProvisionResponse? = resp.body()
                    Log.i(
                        TAG,
                        "account provision ok user_id=${body?.userId} created=${body?.created} linked=${body?.linked}"
                    )
                    return ProvisionResult.Success
                }
                val errBody = resp.errorBody()?.string().orEmpty()
                when (resp.code()) {
                    401, 403 -> {
                        val refreshed = authManager.refreshSessionIfJwtExpired(
                            RuntimeException("account provision rejected token (${resp.code()})")
                        )
                        val newToken = supabaseAuth.currentAccessTokenOrNull()
                        if (refreshed && newToken != null && newToken != currentToken) {
                            currentToken = newToken
                            continue
                        }
                        Log.w(TAG, "account provision rejected (${resp.code()}): $errBody")
                        return ProvisionResult.Failed(errBody.ifBlank { "HTTP ${resp.code()}" })
                    }
                    in 400..499 -> {
                        Log.w(TAG, "account provision failed (${resp.code()}): $errBody")
                        return ProvisionResult.Failed(errBody.ifBlank { "HTTP ${resp.code()}" })
                    }
                    in 500..599 -> {
                        Log.w(TAG, "account provision server error ${resp.code()} attempt $attempt/$MAX_ATTEMPTS")
                        lastError = "HTTP ${resp.code()}"
                    }
                    else -> {
                        Log.w(TAG, "account provision unexpected (${resp.code()}): $errBody")
                        return ProvisionResult.Failed(errBody.ifBlank { "HTTP ${resp.code()}" })
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "account provision error (attempt $attempt/$MAX_ATTEMPTS): ${e.message}")
                lastError = e.message
            }
            kotlinx.coroutines.delay(RETRY_BASE_DELAY_MS * (1L shl (attempt - 1)))
        }
        return ProvisionResult.Failed(lastError ?: "provision failed")
    }
}

sealed class ProvisionResult {
    data object Success : ProvisionResult()
    data object Skipped : ProvisionResult()
    data object NotAuthenticated : ProvisionResult()
    data class Failed(val message: String) : ProvisionResult()
}
