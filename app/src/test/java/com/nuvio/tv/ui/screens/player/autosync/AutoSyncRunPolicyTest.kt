package com.nuvio.tv.ui.screens.player.autosync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSyncRunPolicyTest {
    @Test
    fun releaseBuildsKeepSyncOnAndIgnoreTheHiddenTolerance() {
        assertTrue(
            effectiveAutoSyncEnabled(
                developerSettingsVisible = false,
                storedEnabled = false,
                preferredLanguage = null,
            ),
        )
        assertTrue(
            effectiveAggressiveMode(
                developerSettingsVisible = false,
                storedAggressive = false,
            ),
        )
        assertEquals(
            0,
            effectiveSyncToleranceMs(
                developerSettingsVisible = false,
                storedToleranceMs = 300,
            ),
        )
    }

    @Test
    fun debugBuildsFollowTheStoredToleranceAndSwitches() {
        assertFalse(
            effectiveAutoSyncEnabled(
                developerSettingsVisible = true,
                storedEnabled = false,
                preferredLanguage = "es",
            ),
        )
        assertEquals(
            200,
            effectiveSyncToleranceMs(
                developerSettingsVisible = true,
                storedToleranceMs = 200,
            ),
        )
    }
}
