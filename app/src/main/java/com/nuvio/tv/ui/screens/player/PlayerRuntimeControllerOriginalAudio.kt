package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.core.playback.PlaybackCapabilitiesProvider
import kotlinx.coroutines.flow.update

internal fun supportedAudioCodecsFromDevice(): Set<String>? {
    val audio = PlaybackCapabilitiesProvider.snapshot()?.audio ?: return null
    if (audio.isEmpty()) return null
    return withSoftwareAudioCodecs(
        audio.filterValues { it }.keys.map { it.lowercase() }.toSet()
    )
}

internal fun PlayerRuntimeController.tryAutoSelectOriginalAudioTrack(
    audioTracks: List<TrackInfo>
): Int? {
    if (isUserExplicitAudioSelection || audioTracks.isEmpty()) return null
    persistedTrackPreference?.audio?.let { saved ->
        val savedIndex = findMatchingTrackIndex(audioTracks, saved)
        val savedTrack = audioTracks.getOrNull(savedIndex)
        if (savedTrack == null || savedTrack.isSupported) return null
    }
    if (pendingEngineSwitchTrackPreference?.preference?.audio != null) return null
    if (!shouldUseOriginalAudioHeuristic(preferredAudioLanguageSetting)) return null

    val candidates = audioTracks.map { track ->
        AudioTrackCandidate(
            index = track.index,
            language = track.language,
            name = track.name,
            isCommentary = track.isCommentary,
            codec = track.codec,
            isSupported = track.isSupported,
        )
    }
    val supported = supportedAudioCodecsFromDevice()
    if (noPlayableAudioTrack(candidates, supported)) {
        skipStreamForUnsupportedAudio()
        return null
    }
    val pick = pickPreferredAudioTrackIndex(
        tracks = candidates,
        originalLanguage = contentLanguage,
        secondaryLanguage = secondaryPreferredAudioLanguageSetting,
        deviceLanguages = resolveDeviceAudioLanguages(),
        preferredAudioLanguage = preferredAudioLanguageSetting,
        supportedAudioCodecs = supported,
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
