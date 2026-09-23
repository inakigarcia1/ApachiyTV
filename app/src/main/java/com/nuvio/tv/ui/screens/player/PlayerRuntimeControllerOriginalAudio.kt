package com.nuvio.tv.ui.screens.player

import kotlinx.coroutines.flow.update

internal fun PlayerRuntimeController.tryAutoSelectOriginalAudioTrack(
    audioTracks: List<TrackInfo>
): Int? {
    if (isUserExplicitAudioSelection || audioTracks.isEmpty()) return null
    if (persistedTrackPreference?.audio != null) return null
    if (pendingEngineSwitchTrackPreference?.preference?.audio != null) return null
    if (!shouldUseOriginalAudioHeuristic(preferredAudioLanguageSetting)) return null

    val candidates = audioTracks.map { track ->
        AudioTrackCandidate(
            index = track.index,
            language = track.language,
            name = track.name,
            isCommentary = track.isCommentary
        )
    }
    val pick = pickPreferredAudioTrackIndex(
        tracks = candidates,
        originalLanguage = contentLanguage,
        secondaryLanguage = secondaryPreferredAudioLanguageSetting,
        deviceLanguages = resolveDeviceAudioLanguages(),
        preferredAudioLanguage = preferredAudioLanguageSetting
    ) ?: return null

    val currentlySelected = audioTracks.indexOfFirst { it.isSelected }
    if (pick == currentlySelected) return pick

    selectAudioTrack(pick, fromUser = false)
    return pick
}

internal fun PlayerRuntimeController.reapplyOriginalAudioLanguagePreferences() {
    val resolvedAudioLanguages = resolvePreferredAudioLanguages(
        preferredAudioLanguage = preferredAudioLanguageSetting,
        secondaryPreferredAudioLanguage = secondaryPreferredAudioLanguageSetting,
        deviceLanguages = resolveDeviceAudioLanguages(),
        contentOriginalLanguage = contentLanguage
    )
    if (resolvedAudioLanguages != mpvPreferredAudioLanguages) {
        mpvPreferredAudioLanguages = resolvedAudioLanguages
        if (isUsingMpvEngine()) {
            mpvView?.applyAudioLanguagePreferences(resolvedAudioLanguages)
        }
    }
    _exoPlayer?.let { player ->
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setPreferredAudioLanguages(*resolvedAudioLanguages.toTypedArray())
            .build()
    }
    tryAutoSelectOriginalAudioTrack(_uiState.value.audioTracks)?.let { index ->
        _uiState.update { it.copy(selectedAudioTrackIndex = index) }
    }
}
