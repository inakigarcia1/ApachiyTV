package com.nuvio.tv.ui.screens.player

import android.util.Log
import com.nuvio.tv.core.diagnostics.PlaybackFailureReport
import com.nuvio.tv.core.diagnostics.SentryInitializer
import com.nuvio.tv.core.diagnostics.playbackReportHost
import com.nuvio.tv.core.diagnostics.scrubPlaybackText
import com.nuvio.tv.data.local.InternalPlayerEngine

internal fun PlayerRuntimeController.reportVisiblePlaybackError(displayMessage: String) {
    if (reportedVisiblePlaybackError == displayMessage) return
    reportedVisiblePlaybackError = displayMessage

    val technical = lastPlaybackIssueError?.takeIf { it.displayMessage == displayMessage }
    val state = _uiState.value
    val resolution = listOfNotNull(currentVideoWidth, currentVideoHeight)
        .takeIf { it.size == 2 }
        ?.joinToString("x")
    val codec = listOfNotNull(currentVideoCodec, resolution).joinToString(" ").ifBlank { null }
    val report = PlaybackFailureReport(
        message = scrubPlaybackText(displayMessage) ?: "playback failed",
        engine = currentInternalPlayerEngine.playbackFailureEngine(),
        host = playbackReportHost(currentStreamUrl),
        provider = scrubPlaybackText(currentAddonName),
        streamType = if (isTorrentStream) "torrent" else scrubPlaybackText(contentType),
        streamLabel = scrubPlaybackText(state.currentStreamName ?: streamName),
        contentId = scrubPlaybackText(contentId ?: currentVideoId),
        title = scrubPlaybackText(title),
        season = currentSeason,
        episode = currentEpisode,
        errorCode = technical?.errorCodeName?.takeIf { it.isNotBlank() }
            ?: technical?.errorCode?.toString(),
        exceptionClass = scrubPlaybackText(technical?.exceptionClass),
        causeClass = scrubPlaybackText(technical?.causeClass),
        causeMessage = scrubPlaybackText(technical?.causeMessage),
        httpStatus = technical?.httpStatus,
        videoCodec = scrubPlaybackText(codec),
        mimeType = scrubPlaybackText(currentStreamMimeType),
    )
    Log.e(
        "PlaybackFailure",
        "playback failure engine=${report.engine} host=${report.host} code=${report.errorCode} message=${report.message}",
    )
    SentryInitializer.reportPlaybackFailure(report)
}

internal fun PlayerRuntimeController.clearVisiblePlaybackErrorReport() {
    reportedVisiblePlaybackError = null
}

private fun InternalPlayerEngine.playbackFailureEngine(): String = when (this) {
    InternalPlayerEngine.MVP_PLAYER -> "tv-libmpv"
    InternalPlayerEngine.EXOPLAYER -> "tv-exoplayer"
    InternalPlayerEngine.AUTO -> "tv-auto"
}
