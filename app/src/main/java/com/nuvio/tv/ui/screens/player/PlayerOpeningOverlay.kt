package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.ui.screens.player.embedded.AddonSubtitleLoadingGate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal fun PlayerRuntimeController.resolvedOpeningOverlayVisible(
    loadingOverlayEnabled: Boolean = _uiState.value.loadingOverlayEnabled,
): Boolean {
    if (!loadingOverlayEnabled) return false
    val startedAt = openingOverlayStartedAtMs
    val elapsedMs = if (startedAt <= 0L) 0L else (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
    return !AddonSubtitleLoadingGate.shouldDismissOpeningOverlay(
        playerIsLoading = !hasRenderedFirstFrame,
        pipelineDone = subtitlePipelineDone,
        elapsedMs = elapsedMs,
    )
}

internal fun PlayerRuntimeController.tryCompleteOpeningOverlay() {
    val shouldShow = resolvedOpeningOverlayVisible()
    _uiState.update { state ->
        if (state.showLoadingOverlay == shouldShow) {
            state
        } else {
            state.copy(
                showLoadingOverlay = shouldShow,
                loadingMessage = if (!shouldShow) null else state.loadingMessage,
            )
        }
    }
}

internal fun PlayerRuntimeController.resetOpeningOverlayForNewSource() {
    subtitlePipelineDone = false
    openingOverlayStartedAtMs = System.currentTimeMillis()
    startOpeningOverlayTicker()
}

internal fun PlayerRuntimeController.startOpeningOverlayTicker() {
    openingOverlayTickerJob?.cancel()
    openingOverlayTickerJob = scope.launch {
        while (isActive && _uiState.value.showLoadingOverlay) {
            tryCompleteOpeningOverlay()
            delay(200)
        }
    }
}

internal fun PlayerRuntimeController.markSubtitlePipelineDone() {
    subtitlePipelineDone = true
    tryCompleteOpeningOverlay()
}
