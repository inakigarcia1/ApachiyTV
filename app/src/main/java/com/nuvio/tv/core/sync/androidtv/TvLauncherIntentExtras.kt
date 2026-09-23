package com.nuvio.tv.core.sync.androidtv

data class TvLauncherLaunchRequest(
    val contentId: String,
    val contentType: String,
    val videoId: String,
    val title: String,
    val season: Int?,
    val episode: Int?,
    val poster: String?,
    val backdrop: String?,
    val logo: String?,
    val openStreamOptions: Boolean
)

object TvLauncherIntentExtras {
    const val CONTENT_ID = "contentId"
    const val CONTENT_TYPE = "contentType"
    const val VIDEO_ID = "videoId"
    const val TITLE = "title"
    const val SEASON = "season"
    const val EPISODE = "episode"
    const val POSTER = "poster"
    const val BACKDROP = "backdrop"
    const val LOGO = "logo"
    const val OPEN_STREAM_OPTIONS = "openStreamOptions"
}
