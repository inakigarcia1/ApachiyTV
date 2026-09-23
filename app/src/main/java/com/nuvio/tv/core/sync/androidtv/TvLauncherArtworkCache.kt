package com.nuvio.tv.core.sync.androidtv

import android.content.Context
import android.net.Uri
import com.nuvio.tv.domain.model.WatchProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TvLauncherArtworkCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val artDir: File
        get() = TvLauncherArtContentProvider.artDirectory(context)

    suspend fun resolvePosterArtUri(
        progress: WatchProgress,
        previousPublishedUri: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val selection = selectTvLauncherPosterArt(progress)
        val cacheKey = tvLauncherArtCacheKey(progress)
        val resumeFile = File(artDir, "resume_$cacheKey.jpg")
        val remoteUrl = selection.remoteUrl

        if (remoteUrl == null) {
            if (resumeFile.exists() && resumeFile.length() > 0L) {
                return@withContext TvLauncherArtContentProvider.uriFor(context, resumeFile.name).toString()
            }
            return@withContext previousPublishedUri?.takeIf { it.isNotBlank() }
        }

        if (!remoteUrl.startsWith("http://", ignoreCase = true) &&
            !remoteUrl.startsWith("https://", ignoreCase = true)
        ) {
            return@withContext remoteUrl
        }

        val extension = remoteUrl.substringAfterLast('.', "jpg").substringBefore('?').take(8)
        val safeExtension = extension.ifBlank { "jpg" }
        val cachedName = "${cacheKey.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.$safeExtension"
        val cachedFile = File(artDir, cachedName)
        artDir.mkdirs()

        if (!cachedFile.exists() || cachedFile.length() == 0L) {
            runCatching {
                okHttpClient.newCall(Request.Builder().url(remoteUrl).build()).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.byteStream()?.use { input ->
                            cachedFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
            }
        }

        if (cachedFile.exists() && cachedFile.length() > 0L) {
            TvLauncherArtContentProvider.uriFor(context, cachedFile.name).toString()
        } else {
            remoteUrl
        }
    }

    fun resumeFrameFile(progress: WatchProgress): File {
        val cacheKey = tvLauncherArtCacheKey(progress)
        return File(artDir, "resume_$cacheKey.jpg")
    }

    fun resumeFrameUri(progress: WatchProgress): Uri? {
        val file = resumeFrameFile(progress)
        if (!file.exists() || file.length() == 0L) return null
        return TvLauncherArtContentProvider.uriFor(context, file.name)
    }
}
