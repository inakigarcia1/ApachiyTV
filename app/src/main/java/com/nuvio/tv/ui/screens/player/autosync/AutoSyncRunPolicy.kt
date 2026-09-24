package com.nuvio.tv.ui.screens.player.autosync

internal enum class AutoSyncCandidateScope {
    STARTUP_SEARCH,
    SELECTED_ONLY,
}

internal fun decideAutoSyncStart(enabled: Boolean): AutoSyncStartAction =
    if (enabled) AutoSyncStartAction.RUN else AutoSyncStartAction.ATTACH_ORIGINAL

internal const val SPANISH_SYNC_LANGUAGE = "es"

/** Debug builds follow the stored switch. Release builds always sync. The run is always Castilian Spanish. */
internal fun effectiveAutoSyncEnabled(
    developerSettingsVisible: Boolean,
    storedEnabled: Boolean,
): Boolean = if (developerSettingsVisible) storedEnabled else true

internal fun effectiveAggressiveMode(
    developerSettingsVisible: Boolean,
    storedAggressive: Boolean,
): Boolean = !developerSettingsVisible || storedAggressive

/**
 * Debug builds honor the stored tolerance. Release builds always apply a confident correction,
 * because the control is hidden there.
 */
internal fun effectiveSyncToleranceMs(
    developerSettingsVisible: Boolean,
    storedToleranceMs: Int,
): Int = if (developerSettingsVisible) storedToleranceMs else 0

internal enum class AutoSyncStartAction {
    RUN,
    ATTACH_ORIGINAL,
}

internal fun AutoSyncCandidateScope.alternativeCandidates(
    candidates: List<AutoSyncSubtitleCandidate>,
): List<AutoSyncSubtitleCandidate> =
    if (this == AutoSyncCandidateScope.STARTUP_SEARCH) candidates else emptyList()

internal val AutoSyncCandidateScope.usesAlternativeProvider: Boolean
    get() = this == AutoSyncCandidateScope.STARTUP_SEARCH

internal fun shouldRestoreOriginalSubtitle(
    activeSidecarSubtitleKey: String?,
): Boolean = activeSidecarSubtitleKey == null

