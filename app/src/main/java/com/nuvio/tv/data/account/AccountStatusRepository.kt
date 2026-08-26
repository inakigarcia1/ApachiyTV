package com.nuvio.tv.data.account

import android.util.Log
import com.nuvio.tv.data.remote.device.ApachiyDeviceApi
import com.nuvio.tv.domain.model.AuthState
import com.nuvio.tv.core.auth.AuthManager
import com.nuvio.tv.core.auth.InactiveSubscriptionNotifier
import io.github.jan.supabase.auth.Auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AccountStatusRepository"

@Singleton
class AccountStatusRepository @Inject constructor(
    private val apachiyDeviceApi: ApachiyDeviceApi,
    private val auth: Auth,
    private val authManager: AuthManager,
    private val inactiveSubscriptionNotifier: InactiveSubscriptionNotifier
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isActive = MutableStateFlow<Boolean?>(null)
    val isActive: StateFlow<Boolean?> = _isActive.asStateFlow()

    init {
        scope.launch {
            authManager.authState
                .map { it is AuthState.FullAccount }
                .distinctUntilChanged()
                .collect { isAuthed ->
                    if (isAuthed) {
                        refresh()
                    } else {
                        clear()
                    }
                }
        }
    }

    suspend fun refresh(): Boolean? {
        val token = auth.currentAccessTokenOrNull()
        if (token.isNullOrBlank()) {
            return null
        }

        return runCatching {
            val response = apachiyDeviceApi.getAccountMe("Bearer $token")
            if (!response.isSuccessful) {
                Log.w(TAG, "GET /api/account/me failed: ${response.code()}")
                return null
            }
            val active = response.body()?.user?.isActive ?: return null
            _isActive.value = active
            active
        }.onFailure { error ->
            Log.w(TAG, "Failed to refresh account status", error)
        }.getOrNull()
    }

    fun markInactive() {
        _isActive.value = false
        inactiveSubscriptionNotifier.notifyInactiveSubscription()
    }

    fun clear() {
        _isActive.value = null
    }
}
