package com.nuvio.tv.core.sync.androidtv

import androidx.tvprovider.media.tv.TvContractCompat
import com.nuvio.tv.domain.model.WatchProgress

data class TvLauncherPosterArtSelection(
    val remoteUrl: String?,
    val aspectRatio: Int
)

fun selectTvLauncherPosterArt(progress: WatchProgress): TvLauncherPosterArtSelection {
    val backdrop = progress.backdrop?.trim()?.takeIf { it.isNotBlank() }
    if (backdrop != null) {
        return TvLauncherPosterArtSelection(
            remoteUrl = backdrop,
            aspectRatio = TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9
        )
    }
    val poster = progress.poster?.trim()?.takeIf { it.isNotBlank() }
    if (poster != null) {
        return TvLauncherPosterArtSelection(
            remoteUrl = poster,
            aspectRatio = TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3
        )
    }
    val logo = progress.logo?.trim()?.takeIf { it.isNotBlank() }
    if (logo != null) {
        return TvLauncherPosterArtSelection(
            remoteUrl = logo,
            aspectRatio = TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9
        )
    }
    return TvLauncherPosterArtSelection(remoteUrl = null, aspectRatio = TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
}

fun tvLauncherArtCacheKey(progress: WatchProgress): String {
    return if (progress.season != null && progress.episode != null) {
        "${progress.contentId}_s${progress.season}e${progress.episode}"
    } else {
        progress.contentId
    }
}
