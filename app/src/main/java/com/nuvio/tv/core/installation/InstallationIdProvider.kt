package com.nuvio.tv.core.installation

/**
 * Source of truth for the per-installation Apachiy GUID.
 *
 * - First call on a fresh install generates a UUID v4.
 * - Subsequent calls (same install) return the same value.
 * - The value is stored in `apachiy_installation.xml` SharedPreferences with
 *   `disableAutoBackup()` so uninstalling the app wipes it from disk. A
 *   re-install will therefore generate a new GUID.
 *
 * The implementation lives in [InstallationIdManager].
 */
interface InstallationIdProvider {
    /**
     * Returns a stable, per-installation UUID v4. Thread-safe. Generates + persists
     * a fresh UUID on first call; returns the stored value thereafter.
     */
    fun getInstallationId(): String
}