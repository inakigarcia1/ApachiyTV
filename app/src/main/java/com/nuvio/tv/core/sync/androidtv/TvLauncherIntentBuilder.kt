package com.nuvio.tv.core.sync.androidtv

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.nuvio.tv.MainActivity
import com.nuvio.tv.domain.model.WatchProgress

object TvLauncherIntentBuilder {

    fun buildLaunchIntent(context: Context, progress: WatchProgress): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(TvLauncherIntentExtras.CONTENT_ID, progress.contentId)
            putExtra(TvLauncherIntentExtras.CONTENT_TYPE, progress.contentType)
            putExtra(TvLauncherIntentExtras.VIDEO_ID, progress.videoId)
            putExtra(TvLauncherIntentExtras.TITLE, progress.name)
            progress.season?.let { putExtra(TvLauncherIntentExtras.SEASON, it) }
            progress.episode?.let { putExtra(TvLauncherIntentExtras.EPISODE, it) }
            progress.poster?.takeIf { it.isNotBlank() }?.let {
                putExtra(TvLauncherIntentExtras.POSTER, it)
            }
            progress.backdrop?.takeIf { it.isNotBlank() }?.let {
                putExtra(TvLauncherIntentExtras.BACKDROP, it)
            }
            progress.logo?.takeIf { it.isNotBlank() }?.let {
                putExtra(TvLauncherIntentExtras.LOGO, it)
            }
            putExtra(TvLauncherIntentExtras.OPEN_STREAM_OPTIONS, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }

    fun toIntentUri(context: Context, progress: WatchProgress): Uri {
        return Uri.parse(buildLaunchIntent(context, progress).toUri(Intent.URI_INTENT_SCHEME))
    }
}
