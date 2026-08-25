package com.nuvio.tv.core.auth

import android.util.Log
import com.nuvio.tv.core.installation.InstallationIdProvider
import com.nuvio.tv.data.remote.device.ApachiyDeviceApi
import com.nuvio.tv.domain.model.AuthState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RemoteLogoutWatcher"

@Singleton
class RemoteLogoutWatcher @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val authManager: AuthManager,
    private val installationIdProvider: InstallationIdProvider,
    private val apachiyDeviceApi: ApachiyDeviceApi,
    private val supabaseAuth: Auth
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchJob: Job? = null
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
                        startWatching()
                    } else {
                        stopWatching()
                    }
                }
        }
    }

    private fun startWatching() {
        watchJob?.cancel()
        watchJob = scope.launch {
            val installationId = installationIdProvider.getInstallationId()
            val channel = supabaseClient.channel("apachiy-device-logout-$installationId")
            try {
                val changes = channel.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
                    table = "user_devices"
                }
                val collector = launch {
                    changes.collect { action ->
                        val deletedInstallationId = action.oldRecord["installation_id"]
                            ?.jsonPrimitive?.contentOrNull
                        val deletedDeviceId = action.oldRecord["id"]
                            ?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                        val registeredDeviceId = installationIdProvider.getRegisteredDeviceId()
                        val matchesInstallation =
                            deletedInstallationId != null && deletedInstallationId == installationId
                        val matchesDeviceId =
                            deletedDeviceId != null &&
                                registeredDeviceId != null &&
                                deletedDeviceId == registeredDeviceId
                        val missingLocally = !matchesInstallation && !matchesDeviceId &&
                            stillRegisteredOnApi(installationId).not()
                        if (!matchesInstallation && !matchesDeviceId && !missingLocally) return@collect
                        Log.w(TAG, "device row deleted remotely; signing out")
                        authManager.signOut(explicit = false)
                    }
                }
                channel.subscribe(blockUntilSubscribed = true)
                collector.join()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.w(TAG, "remote logout watcher failed", error)
            } finally {
                runCatching { channel.unsubscribe() }
            }
        }
    }

    private fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
    }

    private suspend fun stillRegisteredOnApi(installationId: String): Boolean {
        val token = supabaseAuth.currentAccessTokenOrNull() ?: return true
        return runCatching {
            val resp = apachiyDeviceApi.listDevices(bearer = "Bearer $token")
            if (!resp.isSuccessful) return@runCatching true
            val rows = resp.body().orEmpty()
            rows.any { it.installationId.equals(installationId, ignoreCase = true) }
        }.getOrDefault(true)
    }
}
