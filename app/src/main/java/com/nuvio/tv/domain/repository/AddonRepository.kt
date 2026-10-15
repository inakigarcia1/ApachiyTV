package com.nuvio.tv.domain.repository

import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.Addon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface AddonRepository {
    val quietAddonUpdates: Flow<Unit>
        get() = emptyFlow()
    fun getInstalledAddons(): Flow<List<Addon>>
    fun getInstalledAddonUrls(): Flow<List<String>>
    val isRemoteSyncInProgress: Boolean
    var hasCompletedInitialAddonReconciliation: Boolean
    suspend fun fetchAddon(baseUrl: String): NetworkResult<Addon>
    suspend fun addAddon(url: String)
    suspend fun removeAddon(url: String)
    suspend fun setAddonOrder(urls: List<String>)
    suspend fun setAddonEnabled(url: String, enabled: Boolean)
}
