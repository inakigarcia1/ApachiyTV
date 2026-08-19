package com.nuvio.tv.core.sync

import com.nuvio.tv.core.installation.InstallationIdProvider
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

private const val ORIGIN_CLIENT_ID_PARAM = "p_origin_client_id"

/**
 * Adapter that the existing `.rpc("register_current_device", params)` call
 * sites consume unchanged.  Delegates to [InstallationIdProvider] so the
 * source of truth for the GUID is the [com.nuvio.tv.core.installation]
 * package, where it can be tested in isolation.
 */
@Singleton
class SyncClientIdentity @Inject constructor(
    private val installationIdProvider: InstallationIdProvider
) {
    fun currentClientId(): String = installationIdProvider.getInstallationId()
}

internal fun JsonObjectBuilder.putSyncOriginClientId(syncClientIdentity: SyncClientIdentity) {
    put(ORIGIN_CLIENT_ID_PARAM, syncClientIdentity.currentClientId())
}