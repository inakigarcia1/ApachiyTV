package com.nuvio.tv.ui.screens.player

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.media3.common.C
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.domain.model.Subtitle
import com.nuvio.tv.ui.screens.player.autosync.AutoSyncPreferences
import com.nuvio.tv.ui.screens.player.autosync.AutomaticSubtitleSync
import com.nuvio.tv.ui.screens.player.autosync.applyAutoSyncSidecarTimeline
import com.nuvio.tv.ui.screens.player.autosync.effectiveAutoSyncEnabled
import com.nuvio.tv.ui.screens.player.autosync.renderRetimedSrt
import com.nuvio.tv.ui.screens.player.autosync.replaceAutoSyncSidecarSubtitle
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

private val autoSyncNoticeHandler = Handler(Looper.getMainLooper())

private fun PlayerRuntimeController.showAutoSyncNotice(message: String) {
    Log.d(PlayerRuntimeController.TAG, message)
    if (!BuildConfig.IS_DEBUG_BUILD) return
    autoSyncNoticeHandler.post {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

internal fun PlayerRuntimeController.maybeRunAutomaticSubtitleSync(selectedSubtitle: Subtitle): Boolean {
    AutoSyncPreferences.ensureLoaded(context)
    if (!effectiveAutoSyncEnabled(
            developerSettingsVisible = BuildConfig.IS_DEBUG_BUILD,
            storedEnabled = AutoSyncPreferences.enabled.value,
            preferredLanguage = currentPlayerSettingsForReport.subtitleStyle.preferredLanguage,
        )
    ) {
        return false
    }
    if (selectedSubtitle.lang.isBlank()) return false
    if (!currentStreamUrl.startsWith("http://", ignoreCase = true) &&
        !currentStreamUrl.startsWith("https://", ignoreCase = true)
    ) {
        return false
    }

    if (!isUsingMpvEngine() && _exoPlayer == null) return false
    showAutoSyncNotice("Auto Sync V2 started")

    automaticSubtitleSyncJob?.cancel()
    val sourceUrlAtStart = currentStreamUrl
    val sourceHeadersAtStart = currentHeaders.toMap()
    val selectedUrl = selectedSubtitle.url

    if (isUsingMpvEngine()) {
        automaticSubtitleSyncJob = scope.launch {
            val resolved = AutomaticSubtitleSync.findTimelineRetime(
                sourceKey = sourceUrlAtStart,
                sourceHeaders = sourceHeadersAtStart,
                selectedSubtitleUrl = selectedUrl,
                selectedSubtitleHeaders = emptyMap(),
                preferredLanguage = selectedSubtitle.lang,
                alternativeSubtitles = emptyList(),
                alternativeSubtitlesProvider = null,
            ) ?: run {
                showAutoSyncNotice("Auto Sync V2 failed: no reliable match")
                return@launch
            }
            if (currentStreamUrl != sourceUrlAtStart) return@launch
            if (_uiState.value.selectedAddonSubtitle?.url != selectedUrl) return@launch
            if (resolved.subtitleUrl != selectedUrl) return@launch
            val body = resolved.subtitleBody ?: return@launch
            val cues = PlayerSubtitleCueParser.parseFromText(body, selectedUrl)
            val rewritten = renderRetimedSrt(cues, resolved.timeline)
            val file = File(context.cacheDir, "autosync-${selectedUrl.hashCode()}.srt")
            file.writeText(rewritten)
            val added = mpvView?.addAndSelectExternalSubtitle(
                url = file.absolutePath,
                title = buildAddonSubtitleTrackId(selectedSubtitle),
                language = PlayerSubtitleUtils.normalizeLanguageCode(selectedSubtitle.lang),
                flags = "select",
            ) == true
            if (!added) {
                showAutoSyncNotice("Auto Sync V2 failed: could not apply sync")
                return@launch
            }
            setSubtitleDelayMs(targetMs = 0, showOverlay = false)
            showAutoSyncNotice(
                buildAutoSyncSuccessToast(
                    replacedSubtitle = false,
                    scale = resolved.timeline.alignmentScale,
                    interceptMs = resolved.timeline.alignmentInterceptMs,
                ),
            )
        }
        return true
    }

    val player = _exoPlayer ?: return false
    if (!canAttachAddonSubtitleViaSidecar(selectedSubtitle)) {
        showAutoSyncNotice("Auto Sync V2 failed: unsupported subtitle renderer")
        return false
    }

    val started = startSidecarAddonSubtitle(
        subtitle = selectedSubtitle,
        rawBodyLoader = {
            AutomaticSubtitleSync.downloadSubtitleBody(
                url = selectedUrl,
                headers = emptyMap(),
            )
        },
    )
    if (!started) {
        showAutoSyncNotice("Auto Sync V2 failed: subtitle could not be loaded")
        return false
    }

    val selectedBodyDeferred = sidecarRawBodyDeferredFor(selectedUrl)
    player.trackSelectionParameters = player.trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        .build()

    automaticSubtitleSyncJob = scope.launch {
        var noSubtitleTracks = false
        val resolved = AutomaticSubtitleSync.findTimelineRetime(
            sourceKey = sourceUrlAtStart,
            sourceHeaders = sourceHeadersAtStart,
            selectedSubtitleUrl = selectedUrl,
            selectedSubtitleHeaders = emptyMap(),
            selectedSubtitleBodyDeferred = selectedBodyDeferred,
            preferredLanguage = selectedSubtitle.lang,
            alternativeSubtitles = emptyList(),
            alternativeSubtitlesProvider = null,
            onNoSubtitleTracks = { noSubtitleTracks = true },
        )
        if (resolved == null) {
            if (activeSidecarSubtitleKey == null) {
                startSidecarAddonSubtitle(selectedSubtitle)
            }
            showAutoSyncNotice(
                if (noSubtitleTracks) "No subtitles in tracks" else "Auto Sync V2 failed: no reliable match",
            )
            return@launch
        }
        if (currentStreamUrl != sourceUrlAtStart) return@launch
        val activeSubtitleUrl = _uiState.value.selectedAddonSubtitle?.url
        if (activeSubtitleUrl != selectedUrl && activeSubtitleUrl != resolved.subtitleUrl) {
            return@launch
        }

        val applied = when {
            resolved.subtitleUrl == selectedUrl -> {
                applyAutoSyncSidecarTimeline(
                    sidecar = this@maybeRunAutomaticSubtitleSync,
                    url = selectedUrl,
                    timeline = resolved.timeline,
                )
            }
            else -> {
                replaceAutoSyncSidecarSubtitle(
                    sidecar = this@maybeRunAutomaticSubtitleSync,
                    expectedCurrentUrl = selectedUrl,
                    url = resolved.subtitleUrl,
                    headers = resolved.subtitleHeaders,
                    rawBody = resolved.subtitleBody,
                    useLibass = requestedUseLibassByUser || activePlayerUsesLibass,
                    timeline = resolved.timeline,
                )
            }
        }
        if (!applied) {
            if (activeSidecarSubtitleKey == null) {
                startSidecarAddonSubtitle(selectedSubtitle)
            }
            showAutoSyncNotice("Auto Sync V2 failed: could not apply sync")
            return@launch
        }
        if (resolved.subtitleUrl != selectedUrl) {
            val chosen = _uiState.value.addonSubtitles.firstOrNull { it.url == resolved.subtitleUrl }
            if (chosen != null) {
                _uiState.update {
                    it.copy(
                        selectedAddonSubtitle = chosen,
                        selectedSubtitleTrackIndex = -1,
                    )
                }
            }
        }
        setSubtitleDelayMs(targetMs = 0, showOverlay = false)
        showAutoSyncNotice(
            buildAutoSyncSuccessToast(
                replacedSubtitle = resolved.subtitleUrl != selectedUrl,
                scale = resolved.timeline.alignmentScale,
                interceptMs = resolved.timeline.alignmentInterceptMs,
            ),
        )
    }
    return true
}

private fun buildAutoSyncSuccessToast(
    replacedSubtitle: Boolean,
    scale: Double,
    interceptMs: Double,
): String {
    val driftCorrected = abs(scale - 1.0) >= 0.0005
    val prefix = if (replacedSubtitle) {
        "Auto Sync V2: subtitle replaced"
    } else {
        "Auto Sync V2 succeeded"
    }
    return when {
        driftCorrected -> "$prefix • drift corrected"
        abs(interceptMs) >= 50.0 -> "$prefix • ${formatAutoSyncOffset(interceptMs)}"
        else -> "$prefix • already in sync"
    }
}

private fun formatAutoSyncOffset(offsetMs: Double): String {
    val roundedMs = offsetMs.roundToInt()
    val sign = if (roundedMs > 0) "+" else ""
    return "$sign${roundedMs}ms"
}
