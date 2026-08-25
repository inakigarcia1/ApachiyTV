package com.nuvio.tv.data.remote.device

import android.os.Build
import android.util.Log
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.auth.AuthManager
import com.nuvio.tv.core.auth.DeviceLimitNotifier
import com.nuvio.tv.core.auth.LastAuthKind
import com.nuvio.tv.domain.model.AuthState
import com.nuvio.tv.core.installation.InstallationIdProvider
import com.nuvio.tv.data.remote.device.dto.DeviceRegistrationError
import com.nuvio.tv.data.remote.device.dto.DeviceRegistrationRequest
import com.nuvio.tv.data.remote.device.dto.DeviceRegistrationResponse
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val TAG = "ApachiyDeviceRegistrar"
private const val MAX_ATTEMPTS = 3
private const val RETRY_BASE_DELAY_MS = 5_000L
private const val MIN_RETRY_GAP_MS = 5_000L
private const val MAX_DEVICE_NAME_LENGTH = 160

/**
 * Sends the [InstallationIdProvider] GUID to the operator's Apachiy .NET API
 * after a successful Supabase auth (sign-in or sign-up).
 *
 * Behaviour:
 * - **Idempotent**: the server's `POST /v1/devices/register` is idempotent on
 *   `(user, installation_id)`. Calling it 20 times for the same user+GUID
 *   produces a single row.
 * - **Resilient**: a transient network failure does NOT destroy the auth
 *   session. Up to [MAX_ATTEMPTS] retries with exponential backoff.
 * - **Non-blocking**: errors are logged and the user keeps their session.
 *   Only a hard `revoked: true` response is treated as a forced sign-out.
 */
@Singleton
class DeviceRegistrar @Inject constructor(
    private val authManager: AuthManager,
    private val supabaseAuth: Auth,
    private val installationIdProvider: InstallationIdProvider,
    private val apachiyDeviceApi: ApachiyDeviceApi,
    private val deviceLimitNotifier: DeviceLimitNotifier,
    @Named("apachiy") private val json: Json
) {
    @Suppress("unused")
    private val authRef: Auth = supabaseAuth  // keep an explicit dep for clarity

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var lastAttemptMs = 0L
    private var observingStarted = false

    @Synchronized
    fun startObserving() {
        if (observingStarted) return
        observingStarted = true
        scope.launch {
            authManager.authState
                .map { it is AuthState.FullAccount }
                .distinctUntilChanged()
                .collect { isAuthed ->
                    if (isAuthed) {
                        registerNow()
                    } else {
                        installationIdProvider.clearRegisteredDeviceId()
                    }
                }
        }
    }

    fun requestForegroundRegistration() {
        scope.launch { registerNow() }
    }

    private suspend fun registerNow() {
        if (BuildConfig.APACHIY_API_BASE_URL.isBlank()) {
            Log.d(TAG, "APACHIY_API_BASE_URL empty; skipping device registration.")
            return
        }
        val state = authManager.authState.value
        if (state !is AuthState.FullAccount) {
            return
        }
        val accessToken = supabaseAuth.currentAccessTokenOrNull() ?: run {
            Log.w(TAG, "Authenticated but no access token; will retry on next state change.")
            return
        }
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (now - lastAttemptMs < MIN_RETRY_GAP_MS) {
                return
            }
            lastAttemptMs = now
            val req = buildRequest()
            Log.i(TAG, "registering device installation_id=${req.installationId.take(8)}…")
            attemptWithRetry(accessToken, req)
        }
    }

    private suspend fun attemptWithRetry(accessToken: String, req: DeviceRegistrationRequest) {
        var attempt = 0
        var lastError: Throwable? = null
        var currentToken = accessToken
        while (attempt < MAX_ATTEMPTS) {
            attempt++
            try {
                val resp = apachiyDeviceApi.registerDevice(
                    bearer = "Bearer $currentToken",
                    body = req
                )
                if (resp.isSuccessful) {
                    val body: DeviceRegistrationResponse? = resp.body()
                    val deviceId = body?.deviceId?.takeIf { it > 0L }
                    Log.i(TAG, "device registered id=$deviceId created=${body?.created} revoked=${body?.revoked}")
                    if (deviceId != null) {
                        installationIdProvider.setRegisteredDeviceId(deviceId)
                    }
                    if (body?.revoked == true) {
                        Log.w(TAG, "device was reported revoked; signing out")
                        authManager.signOut()
                    }
                    return
                }
                val errBody = resp.errorBody()?.string().orEmpty()
                val err = runCatching {
                    json.decodeFromString(DeviceRegistrationError.serializer(), errBody)
                }.getOrNull()
                when (resp.code()) {
                    401, 403 -> {
                        val refreshed = authManager.refreshSessionIfJwtExpired(
                            RuntimeException("device api rejected token (${resp.code()})")
                        )
                        val newToken = supabaseAuth.currentAccessTokenOrNull()
                        if (refreshed && newToken != null && newToken != currentToken) {
                            currentToken = newToken
                            continue
                        }
                        Log.w(TAG, "device registration rejected (${resp.code()}): $errBody")
                        return
                    }
                    410, 423 -> {
                        Log.w(TAG, "device revoked on server; signing out")
                        authManager.signOut()
                        return
                    }
                    409 -> {
                        if (err?.error == "max_devices_exceeded") {
                            handleMaxDevicesExceeded()
                        } else {
                            Log.w(TAG, "device registration conflict (409): $errBody")
                        }
                        return
                    }
                    in 500..599 -> {
                        Log.w(TAG, "server error ${resp.code()}; will retry (attempt $attempt/$MAX_ATTEMPTS) err=$err")
                        lastError = RuntimeException("HTTP ${resp.code()}")
                    }
                    else -> {
                        Log.w(TAG, "device registration failed (${resp.code()}): $errBody")
                        return
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "device registration error (attempt $attempt/$MAX_ATTEMPTS): ${e.message}")
                lastError = e
            }
            delay(RETRY_BASE_DELAY_MS * (1L shl (attempt - 1)))
        }
        Log.e(TAG, "device registration gave up after $MAX_ATTEMPTS attempts: ${lastError?.message}")
    }

    private suspend fun handleMaxDevicesExceeded() {
        Log.w(TAG, "max devices exceeded for user")
        deviceLimitNotifier.notifyMaxDevicesExceeded()
        if (authManager.lastAuthKind != LastAuthKind.SignUp) {
            authManager.signOut(explicit = false)
        }
    }

    private fun buildRequest(): DeviceRegistrationRequest {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val deviceModel = when {
            model.isBlank() -> "Android TV"
            manufacturer.isBlank() -> model
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }.take(MAX_DEVICE_NAME_LENGTH)
        val osVersion = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() } ?: Build.VERSION.SDK_INT.toString()
        return DeviceRegistrationRequest(
            installationId = installationIdProvider.getInstallationId(),
            platform = "android_tv",
            app = "apachiy",
            appVersion = BuildConfig.VERSION_NAME,
            osVersion = osVersion,
            deviceModel = deviceModel
        )
    }
}