package com.nuvio.tv.core.recommendations

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.nuvio.tv.core.sync.androidtv.TvLauncherIntentBuilder
import com.nuvio.tv.core.sync.androidtv.selectTvLauncherPosterArt
import com.nuvio.tv.domain.model.WatchProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgramBuilder @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun watchNextId(progress: WatchProgress): String =
        if (progress.season != null && progress.episode != null)
            "wn_${progress.contentId}_s${progress.season}e${progress.episode}"
        else
            "wn_${progress.contentId}"

    fun buildWatchNextProgram(
        progress: WatchProgress,
        resolvedPosterArtUri: String? = null
    ): WatchNextProgram {
        val isMovie = progress.contentType == "movie"
        val programType = if (isMovie) {
            TvContractCompat.WatchNextPrograms.TYPE_MOVIE
        } else {
            TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE
        }

        val builder = WatchNextProgram.Builder()
            .setType(programType)
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setTitle(progress.name)
            .setLastEngagementTimeUtcMillis(progress.lastWatched)
            .setInternalProviderId(watchNextId(progress))
            .setIntentUri(buildPlayUri(progress))

        val artSelection = selectTvLauncherPosterArt(progress)
        builder.setPosterArtAspectRatio(artSelection.aspectRatio)
        resolvedPosterArtUri?.let { artUri ->
            builder.setPosterArtUri(Uri.parse(artUri))
        }

        if (progress.duration > 0) {
            builder.setLastPlaybackPositionMillis(progress.position.toInt())
            builder.setDurationMillis(progress.duration.toInt())
        }

        if (!isMovie) {
            progress.season?.let { builder.setSeasonNumber(it) }
            progress.episode?.let { builder.setEpisodeNumber(it) }
            progress.episodeTitle?.let { builder.setEpisodeTitle(it) }
        }

        return builder.build()
    }

    fun upsertWatchNextProgram(program: WatchNextProgram, internalId: String) {
        try {
            val existingId = findWatchNextByInternalId(internalId)
            if (existingId != null) {
                val uri = TvContractCompat.buildWatchNextProgramUri(existingId)
                context.contentResolver.update(uri, program.toContentValues(), null, null)
            } else {
                context.contentResolver.insert(
                    TvContractCompat.WatchNextPrograms.CONTENT_URI,
                    program.toContentValues()
                )
            }
        } catch (_: Exception) {
        }
    }

    fun removeWatchNextProgram(internalId: String) {
        try {
            val existingId = findWatchNextByInternalId(internalId) ?: return
            val uri = TvContractCompat.buildWatchNextProgramUri(existingId)
            context.contentResolver.delete(uri, null, null)
        } catch (_: Exception) {
        }
    }

    fun removeWatchNextByContentId(contentId: String) {
        try {
            val cursor = context.contentResolver.query(
                TvContractCompat.WatchNextPrograms.CONTENT_URI,
                arrayOf(
                    TvContractCompat.WatchNextPrograms._ID,
                    TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID
                ),
                null, null, null
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val idIdx = it.getColumnIndex(
                        TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID
                    )
                    if (idIdx >= 0) {
                        val providerId = it.getString(idIdx)
                        if (watchNextIdMatchesContentId(providerId, contentId)) {
                            val pkIdx = it.getColumnIndex(TvContractCompat.WatchNextPrograms._ID)
                            if (pkIdx >= 0) {
                                val uri = TvContractCompat.buildWatchNextProgramUri(it.getLong(pkIdx))
                                context.contentResolver.delete(uri, null, null)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    fun getAllWatchNextInternalIds(): Set<String> {
        val result = mutableSetOf<String>()
        try {
            val cursor = context.contentResolver.query(
                TvContractCompat.WatchNextPrograms.CONTENT_URI,
                arrayOf(TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID),
                null, null, null
            )
            cursor?.use {
                val idIdx = it.getColumnIndex(TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID)
                if (idIdx >= 0) {
                    while (it.moveToNext()) {
                        val providerId = it.getString(idIdx)
                        if (providerId?.startsWith("wn_") == true) {
                            result.add(providerId)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    fun clearAllWatchNextPrograms() {
        var cursor: android.database.Cursor? = null
        try {
            cursor = context.contentResolver.query(
                TvContractCompat.WatchNextPrograms.CONTENT_URI,
                arrayOf(
                    TvContractCompat.WatchNextPrograms._ID,
                    TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID
                ),
                null, null, null
            )
            cursor?.let {
                while (it.moveToNext()) {
                    val idIdx = it.getColumnIndex(
                        TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID
                    )
                    if (idIdx >= 0) {
                        val providerId = it.getString(idIdx)
                        if (providerId?.startsWith("wn_") == true) {
                            val pkIdx = it.getColumnIndex(TvContractCompat.WatchNextPrograms._ID)
                            if (pkIdx >= 0) {
                                val uri = TvContractCompat.buildWatchNextProgramUri(it.getLong(pkIdx))
                                context.contentResolver.delete(uri, null, null)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
    }

    private fun findWatchNextByInternalId(internalId: String): Long? {
        return try {
            val cursor = context.contentResolver.query(
                TvContractCompat.WatchNextPrograms.CONTENT_URI,
                arrayOf(
                    TvContractCompat.WatchNextPrograms._ID,
                    TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID
                ),
                null,
                null,
                null
            )
            var foundId: Long? = null
            cursor?.use {
                while (it.moveToNext()) {
                    val providerIdIdx = it.getColumnIndex(
                        TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID
                    )
                    if (providerIdIdx >= 0) {
                        val currentProviderId = it.getString(providerIdIdx)
                        if (currentProviderId == internalId) {
                            val idIdx = it.getColumnIndex(TvContractCompat.WatchNextPrograms._ID)
                            if (idIdx >= 0) {
                                foundId = it.getLong(idIdx)
                                break
                            }
                        }
                    }
                }
            }
            foundId
        } catch (_: Exception) {
            null
        }
    }

    private fun buildPlayUri(progress: WatchProgress): Uri =
        TvLauncherIntentBuilder.toIntentUri(context, progress)
}

internal fun watchNextIdMatchesContentId(providerId: String?, contentId: String): Boolean {
    val baseId = "wn_$contentId"
    if (providerId == baseId) return true
    if (providerId?.startsWith("${baseId}_s") != true) return false

    val episodeSeparator = providerId.indexOf('e', startIndex = baseId.length + 2)
    return episodeSeparator > baseId.length + 2 &&
        episodeSeparator < providerId.lastIndex &&
        (baseId.length + 2 until episodeSeparator).all { providerId[it].isDigit() } &&
        (episodeSeparator + 1..providerId.lastIndex).all { providerId[it].isDigit() }
}
