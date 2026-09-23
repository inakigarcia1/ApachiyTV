package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.core.sync.androidtv.TvLauncherArtContentProvider
import java.io.File

internal fun PlayerRuntimeController.captureTvResumeFrameIfNeeded() {
    if (!listOf(poster, backdrop, logo).all { it.isNullOrBlank() }) return
    if (!isUsingMpvEngine()) return
    val parentContentId = contentId?.takeIf { it.isNotBlank() } ?: return
    val cacheKey = if (currentSeason != null && currentEpisode != null) {
        "${parentContentId}_s${currentSeason}e${currentEpisode}"
    } else {
        parentContentId
    }
    val target = File(
        TvLauncherArtContentProvider.artDirectory(context),
        "resume_$cacheKey.jpg"
    )
    mpvView?.screenshotVideoFrameToFile(target)
}
