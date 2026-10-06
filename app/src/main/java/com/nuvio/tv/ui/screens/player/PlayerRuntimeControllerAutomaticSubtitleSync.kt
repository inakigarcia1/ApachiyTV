package com.nuvio.tv.ui.screens.player

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.media3.common.C
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.domain.model.Subtitle
import com.nuvio.tv.ui.screens.player.autosync.AutoSyncPreferences
import com.nuvio.tv.ui.screens.player.autosync.AutoSyncResolvedTimeline
import com.nuvio.tv.ui.screens.player.autosync.AutomaticSubtitleSync
import com.nuvio.tv.ui.screens.player.autosync.applyAutoSyncSidecarTimeline
import com.nuvio.tv.ui.screens.player.audiosync.AudioSyncFallback
import com.nuvio.tv.ui.screens.player.autosync.AutoSyncSubtitleCandidate
import com.nuvio.tv.ui.screens.player.autosync.SPANISH_SYNC_LANGUAGE
import com.nuvio.tv.ui.screens.player.autosync.effectiveAutoSyncEnabled
import com.nuvio.tv.ui.screens.player.autosync.effectiveSyncToleranceMs
import com.nuvio.tv.ui.screens.player.autosync.maxAlignmentShiftMs
import com.nuvio.tv.ui.screens.player.autosync.renderRetimedSrt
import com.nuvio.tv.ui.screens.player.autosync.replaceAutoSyncSidecarSubtitle
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.roundToInt

private const val OPEN_SUBTITLES_ADDON = "https://opensubtitles-v3.strem.io"
private const val MAX_EXTRA_CANDIDATES = 5

private fun syncReferenceLanguage(subtitle: Subtitle): String {
    val castilian = PlayerSubtitleUtils.isCastilianSpanishLanguage(
        language = subtitle.lang,
        name = subtitle.addonName,
        trackId = subtitle.id,
    )
    return if (castilian) SPANISH_SYNC_LANGUAGE else subtitle.lang
}

private val autoSyncNoticeHandler = Handler(Looper.getMainLooper())

internal fun PlayerRuntimeController.showAutoSyncNotice(message: String) {
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
        val extraCandidates = startExtraSubtitleLookup(syncReferenceLanguage(selectedSubtitle))
        automaticSubtitleSyncJob = scope.launch {
            val resolved = AutomaticSubtitleSync.findTimelineRetime(
                sourceKey = sourceUrlAtStart,
                sourceHeaders = sourceHeadersAtStart,
                selectedSubtitleUrl = selectedUrl,
                selectedSubtitleHeaders = emptyMap(),
                preferredLanguage = syncReferenceLanguage(selectedSubtitle),
                alternativeSubtitles = extraCandidates,
                alternativeSubtitlesProvider = { extraCandidates.toList() },
            ) ?: run {
                showAutoSyncNotice("Auto Sync V2 failed: no reliable match")
                return@launch
            }
            if (currentStreamUrl != sourceUrlAtStart) return@launch
            if (_uiState.value.selectedAddonSubtitle?.url != selectedUrl) return@launch
            val withinToleranceMs = withinAutoSyncToleranceMs(selectedUrl, resolved)
            if (withinToleranceMs != null) {
                setSubtitleDelayMs(targetMs = 0, showOverlay = false)
                showAutoSyncNotice(withinToleranceToast(withinToleranceMs))
                return@launch
            }
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
    val userChoseSubtitle = isUserExplicitSubtitleSelection
    AudioSyncFallback.stop(this)
    player.trackSelectionParameters = player.trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        .build()

    val extraCandidates = startExtraSubtitleLookup(syncReferenceLanguage(selectedSubtitle))
    automaticSubtitleSyncJob = scope.launch {
        var noSubtitleTracks = false
        var matched = AutomaticSubtitleSync.findTimelineRetime(
            sourceKey = sourceUrlAtStart,
            sourceHeaders = sourceHeadersAtStart,
            selectedSubtitleUrl = selectedUrl,
            selectedSubtitleHeaders = emptyMap(),
            selectedSubtitleBodyDeferred = selectedBodyDeferred,
            preferredLanguage = syncReferenceLanguage(selectedSubtitle),
            alternativeSubtitles = extraCandidates,
            alternativeSubtitlesProvider = { extraCandidates.toList() },
            onNoSubtitleTracks = { noSubtitleTracks = true },
        )
        if (matched == null &&
            !noSubtitleTracks &&
            !userChoseSubtitle &&
            currentStreamUrl == sourceUrlAtStart &&
            _uiState.value.selectedAddonSubtitle?.url == selectedUrl
        ) {
            val seed = secondaryLanguageSearchSeed(
                candidates = _uiState.value.addonSubtitles.map { subtitle ->
                    AutoSyncSubtitleCandidate(subtitle.url, subtitle.lang, subtitle.addonName)
                },
                selectedUrl = selectedUrl,
                searchedLanguage = selectedSubtitle.lang,
                secondaryLanguage = _uiState.value.subtitleStyle.secondaryPreferredLanguage,
            )
            if (seed != null) {
                matched = AutomaticSubtitleSync.findTimelineRetime(
                    sourceKey = sourceUrlAtStart,
                    sourceHeaders = sourceHeadersAtStart,
                    selectedSubtitleUrl = seed.url,
                    selectedSubtitleHeaders = emptyMap(),
                    preferredLanguage = seed.language,
                )
                showAutoSyncNotice(
                    if (matched != null) "Auto Sync V2: secondary language matched" else "Auto Sync V2: secondary language missed",
                )
            }
        }
        if (matched == null) {
            val stillOnSubtitle = currentStreamUrl == sourceUrlAtStart &&
                _uiState.value.selectedAddonSubtitle?.url == selectedUrl
            val handedToAudio = noSubtitleTracks &&
                stillOnSubtitle &&
                activeSidecarSubtitleKey == selectedUrl &&
                AudioSyncFallback.of(this@maybeRunAutomaticSubtitleSync)?.let { fallback ->
                    fallback.arm(mayReplaceSubtitle = !userChoseSubtitle)
                    fallback.takeOver(selectedUrl)
                } == true
            if (handedToAudio) return@launch
            if (stillOnSubtitle && activeSidecarSubtitleKey == null) {
                startSidecarAddonSubtitle(selectedSubtitle)
            }
            showAutoSyncNotice(
                if (noSubtitleTracks) "No subtitles in tracks" else "Auto Sync V2 failed: no reliable match",
            )
            return@launch
        }
        AudioSyncFallback.stop(this@maybeRunAutomaticSubtitleSync)
        val resolved = matched ?: return@launch
        if (currentStreamUrl != sourceUrlAtStart) return@launch
        val activeSubtitleUrl = _uiState.value.selectedAddonSubtitle?.url
        if (activeSubtitleUrl != selectedUrl && activeSubtitleUrl != resolved.subtitleUrl) {
            return@launch
        }

        val withinToleranceMs = withinAutoSyncToleranceMs(selectedUrl, resolved)
        val applied = when {
            withinToleranceMs != null -> activeSidecarSubtitleKey == selectedUrl
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
            if (withinToleranceMs != null) {
                withinToleranceToast(withinToleranceMs)
            } else {
                buildAutoSyncSuccessToast(
                    replacedSubtitle = resolved.subtitleUrl != selectedUrl,
                    scale = resolved.timeline.alignmentScale,
                    interceptMs = resolved.timeline.alignmentInterceptMs,
                )
            },
        )
    }
    return true
}

private fun withinAutoSyncToleranceMs(
    selectedUrl: String,
    resolved: AutoSyncResolvedTimeline,
): Int? {
    val toleranceMs = effectiveSyncToleranceMs(
        developerSettingsVisible = BuildConfig.IS_DEBUG_BUILD,
        storedToleranceMs = AutoSyncPreferences.syncToleranceMs.value,
    )
    if (toleranceMs <= 0 || resolved.subtitleUrl != selectedUrl) return null
    return toleranceMs.takeIf { resolved.timeline.maxAlignmentShiftMs() <= it }
}

private fun withinToleranceToast(toleranceMs: Int): String =
    "Auto Sync V2 • in sync (within $toleranceMs ms tolerance)"

/**
 * Same-language files from the open OpenSubtitles addon. AutoSync scores them against the
 * embedded tracks and keeps one only when it actually fits this release. The list fills while
 * that search runs.
 */
private fun PlayerRuntimeController.startExtraSubtitleLookup(
    language: String,
): CopyOnWriteArrayList<AutoSyncSubtitleCandidate> {
    val extra = CopyOnWriteArrayList<AutoSyncSubtitleCandidate>()
    val type = contentType?.takeIf { it.isNotBlank() } ?: return extra
    val id = (videoId ?: currentVideoId)?.takeIf { it.isNotBlank() } ?: return extra
    scope.launch {
        val found = runCatching { fetchOpenSubtitleCandidates(type, id, language) }
            .getOrElse { error ->
                Log.w(PlayerRuntimeController.TAG, "extra subtitle lookup failed for $id: ${error.message}")
                emptyList()
            }
        extra.addAll(found)
    }
    return extra
}

private suspend fun fetchOpenSubtitleCandidates(
    type: String,
    videoId: String,
    language: String,
): List<AutoSyncSubtitleCandidate> {
    val canonicalType = if (type.equals("tv", ignoreCase = true)) "series" else type.lowercase()
    val url = "$OPEN_SUBTITLES_ADDON/subtitles/$canonicalType/$videoId.json"
    val body = AutomaticSubtitleSync.downloadSubtitleBody(url = url, headers = emptyMap())
    val array = org.json.JSONObject(body).optJSONArray("subtitles") ?: org.json.JSONArray()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val subtitleUrl = item.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val lang = item.optString("lang")
        if (!PlayerSubtitleUtils.matchesLanguageCode(lang, language)) return@mapNotNull null
        AutoSyncSubtitleCandidate(url = subtitleUrl, language = lang, name = "OpenSubtitles")
    }.distinctBy { it.url }.take(MAX_EXTRA_CANDIDATES)
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

private val nonLanguageSecondaryValues = setOf("none", "device", "forced", "default")

/** One subtitle in the secondary language, for this playback only. Not saved as a preference. */
private fun secondaryLanguageSearchSeed(
    candidates: List<AutoSyncSubtitleCandidate>,
    selectedUrl: String,
    searchedLanguage: String?,
    secondaryLanguage: String?,
): AutoSyncSubtitleCandidate? {
    val raw = secondaryLanguage?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    if (raw in nonLanguageSecondaryValues) return null
    val secondary = PlayerSubtitleUtils.normalizeLanguageCode(raw).takeIf { it.isNotBlank() } ?: return null
    if (PlayerSubtitleUtils.matchesLanguageCode(searchedLanguage, secondary)) return null
    return candidates.firstOrNull { candidate ->
        candidate.url.isNotBlank() &&
            candidate.url != selectedUrl &&
            PlayerSubtitleUtils.matchesLanguageCode(candidate.language, secondary)
    }
}

private fun formatAutoSyncOffset(offsetMs: Double): String {
    val roundedMs = offsetMs.roundToInt()
    val sign = if (roundedMs > 0) "+" else ""
    return "$sign${roundedMs}ms"
}
