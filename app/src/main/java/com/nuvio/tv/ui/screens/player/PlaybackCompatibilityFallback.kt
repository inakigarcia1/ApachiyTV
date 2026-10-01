package com.nuvio.tv.ui.screens.player

import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlaybackException
import com.nuvio.tv.core.playback.PlaybackCapabilitiesProvider
import com.nuvio.tv.domain.model.Stream
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun PlaybackException.isCompatibilityFailure(): Boolean = errorCode in setOf(
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
)

internal fun PlayerRuntimeController.tryNextStreamAfterCompatibilityFailure(
    error: PlaybackException,
    detailedError: String,
): Boolean {
    if (!error.isCompatibilityFailure()) return false
    if (compatibilityFallbackJob?.isActive == true) return true
    if (compatibilityFallbackVideoId != currentVideoId) {
        compatibilityFallbackVideoId = currentVideoId
        compatibilityAttemptedUrls.clear()
    }
    compatibilityAttemptedUrls.add(currentStreamUrl)
    if (compatibilityAttemptedUrls.size > 6) return false

    val format = (error as? ExoPlaybackException)?.rendererFormat
    PlaybackCapabilitiesProvider.recordDecoderFailure(
        mime = format?.sampleMimeType,
        codecs = format?.codecs,
        height = format?.height ?: 0,
    )

    _uiState.update {
        it.copy(
            error = null,
            showLoadingOverlay = true,
            loadingMessage = context.getString(com.nuvio.tv.R.string.player_source_incompatible_trying_next),
        )
    }
    compatibilityFallbackJob = scope.launch {
        if (_uiState.value.sourceAllStreams.isEmpty()) {
            loadSourceStreams(forceRefresh = false)
        }
        val next = pollNextCompatibleStream()
        if (next != null) {
            switchToSourceStream(next)
        } else {
            _uiState.update {
                it.copy(
                    error = detailedError,
                    showLoadingOverlay = false,
                    loadingMessage = null,
                )
            }
        }
    }
    return true
}

private suspend fun PlayerRuntimeController.pollNextCompatibleStream(): Stream? {
    repeat(20) {
        nextCompatibleStream()?.let { return it }
        val state = _uiState.value
        if (!state.isLoadingSourceStreams && state.sourceAllStreams.isNotEmpty()) return null
        if (!state.isLoadingSourceStreams && it > 4) return null
        delay(250)
    }
    return nextCompatibleStream()
}

private fun PlayerRuntimeController.nextCompatibleStream(): Stream? {
    return _uiState.value.sourceAllStreams.firstOrNull { stream ->
        val url = stream.getStreamUrl()
        !url.isNullOrBlank() && url !in compatibilityAttemptedUrls && url != currentStreamUrl
    }
}
