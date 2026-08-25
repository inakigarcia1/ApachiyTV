package com.nuvio.tv.core.installation

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ApachiyInstallation"
private const val PREFS_NAME = "apachiy_installation"
private const val KEY_ID = "installation_id"
private const val KEY_REGISTERED_DEVICE_ID = "registered_device_id"
private val UUID_V4_REGEX = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"
)

/**
 * Generates, persists, and returns a stable per-installation UUID v4.
 *
 * Storage: [PREFS_NAME] SharedPreferences with [Context.MODE_PRIVATE].
 * The app manifest sets `android:allowBackup="false"`, so this value is not
 * restored after uninstall. A re-install therefore always starts fresh.
 *
 * The first call generates a UUID v4 from [UUID.randomUUID]. A corrupted or
 * legacy non-UUID value in storage is replaced with a fresh one (the previous
 * `nuvio-tv-<32 chars>` value is intentionally discarded; legacy users get a
 * new GUID once).
 */
@Singleton
class InstallationIdManager @Inject constructor(
    @ApplicationContext private val context: Context
) : InstallationIdProvider {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    override fun getInstallationId(): String {
        val stored = prefs.getString(KEY_ID, null)
        if (stored != null && UUID_V4_REGEX.matches(stored)) {
            return stored
        }
        val fresh = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_ID, fresh).apply()
        if (stored != null) {
            Log.i(TAG, "Replaced legacy/corrupt installation id with fresh UUID v4")
        } else {
            Log.i(TAG, "Generated new installation id: $fresh")
        }
        return fresh
    }

    @Synchronized
    override fun getRegisteredDeviceId(): Long? {
        if (!prefs.contains(KEY_REGISTERED_DEVICE_ID)) return null
        return prefs.getLong(KEY_REGISTERED_DEVICE_ID, -1L)
    }

    @Synchronized
    override fun setRegisteredDeviceId(deviceId: Long) {
        prefs.edit().putLong(KEY_REGISTERED_DEVICE_ID, deviceId).commit()
    }

    @Synchronized
    override fun clearRegisteredDeviceId() {
        prefs.edit().remove(KEY_REGISTERED_DEVICE_ID).apply()
    }

    companion object {
        /** Visible for tests. */
        internal const val PREFS_NAME_INTERNAL = PREFS_NAME
    }
}