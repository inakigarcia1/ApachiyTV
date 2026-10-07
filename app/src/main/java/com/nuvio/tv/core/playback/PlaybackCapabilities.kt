package com.nuvio.tv.core.playback

import android.content.Context
import android.os.Build
import android.view.WindowManager
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PlaybackScreenDto(
    val width: Int,
    val height: Int,
)

@Serializable
data class VideoCodecCapabilityDto(
    val profiles: List<String> = emptyList(),
    val maxLevel: String? = null,
    val bitDepths: List<Int> = emptyList(),
    val maxWidth: Int? = null,
    val maxHeight: Int? = null,
    val hdr: List<String> = emptyList(),
)

@Serializable
data class HdrCapabilityDto(
    val hdr10: Boolean? = null,
    val hdr10Plus: Boolean? = null,
    val dolbyVision: Boolean? = null,
    val hlg: Boolean? = null,
    val probed: Boolean? = null,
)

@Serializable
data class ObservedPlaybackFailureDto(
    val codec: String? = null,
    val profile: String? = null,
    val height: Int? = null,
    val mime: String? = null,
    val codecs: String? = null,
    val dolbyVision: Boolean = false,
    val count: Int = 1,
)

@Serializable
data class PlaybackCapabilitiesPayload(
    val platform: String? = null,
    val screen: PlaybackScreenDto? = null,
    val playerBackend: String? = null,
    val codecs: List<String>? = null,
    @SerialName("decoderCapabilities")
    val decoderCapabilities: Map<String, Map<String, Boolean>>? = null,
    val video: Map<String, VideoCodecCapabilityDto>? = null,
    val hdr: HdrCapabilityDto? = null,
    val audio: Map<String, Boolean>? = null,
    val observedFailures: List<ObservedPlaybackFailureDto>? = null,
    val speedMbps: Double? = null,
    val runtimeMinutes: Int? = null,
) {
    fun hasAnyConstraint(): Boolean =
        (screen != null && screen.width > 0 && screen.height > 0)
            || !codecs.isNullOrEmpty()
            || !decoderCapabilities.isNullOrEmpty()
            || !video.isNullOrEmpty()
            || hdr != null
            || !audio.isNullOrEmpty()
            || (speedMbps != null && speedMbps > 0.0)
            || (runtimeMinutes != null && runtimeMinutes > 0)
}

object PlaybackCapabilitiesProvider {
    private var appContext: Context? = null
    private var cached: PlaybackCapabilitiesPayload? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun snapshot(): PlaybackCapabilitiesPayload? {
        cached?.let { return it }
        val context = appContext ?: return null
        cached = probe(context)
        return cached
    }

    fun appendToStreamUrlIfApachiy(
        baseUrl: String,
        streamUrl: String,
        runtimeMinutes: Int? = null,
    ): String {
        if (!isApachiyAddonBase(baseUrl)) return streamUrl
        val payload = playbackPayloadForStream(runtimeMinutes)?.takeIf { it.hasAnyConstraint() } ?: return streamUrl
        val json = playbackJson.encodeToString(payload)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(Charsets.UTF_8))
        val separator = if (streamUrl.contains('?')) '&' else '?'
        return "$streamUrl$separator" + "playback=$encoded"
    }

    fun recordDecoderFailure(mime: String?, codecs: String?, height: Int) {
        val context = appContext ?: return
        PlaybackFailureMemory.record(context, mime, codecs, height)
        cached = null
    }

    private fun probe(context: Context): PlaybackCapabilitiesPayload? {
        val screen = readPhysicalScreen(context) ?: return null
        val probed = runCatching { probeDeviceDecoders(context) }.getOrNull()
        val failures = PlaybackFailureMemory.snapshot(context)
        if (probed == null) {
            return PlaybackCapabilitiesPayload(
                platform = "android-tv",
                screen = screen,
                playerBackend = "exoplayer",
                observedFailures = failures,
            )
        }
        return PlaybackCapabilitiesPayload(
            platform = "android-tv",
            screen = screen,
            playerBackend = "exoplayer",
            codecs = probed.codecs,
            decoderCapabilities = probed.cells,
            video = probed.video,
            hdr = probed.hdr,
            audio = probed.audio,
            observedFailures = failures,
        )
    }

    private fun readPhysicalScreen(context: Context): PlaybackScreenDto? {
        val wm = context.getSystemService(WindowManager::class.java) ?: return null
        val display = context.getSystemService(android.hardware.display.DisplayManager::class.java)
            ?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val panel = choosePanelSize(
            display?.supportedModes?.map { it.physicalWidth to it.physicalHeight }.orEmpty(),
        )
        if (panel != null) return panel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            return PlaybackScreenDto(bounds.width(), bounds.height()).takeIf {
                it.width > 0 && it.height > 0
            }
        }
        @Suppress("DEPRECATION")
        val legacyDisplay = wm.defaultDisplay ?: return null
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        legacyDisplay.getRealMetrics(metrics)
        return PlaybackScreenDto(metrics.widthPixels, metrics.heightPixels).takeIf {
            it.width > 0 && it.height > 0
        }
    }

    private fun playbackPayloadForStream(runtimeMinutes: Int?): PlaybackCapabilitiesPayload? {
        val device = snapshot()
        val speed = com.nuvio.tv.core.network.TorboxSpeedTestHarness.readSample()
            ?.takeIf { it.isValid() }
            ?.speedMbps
        val runtime = runtimeMinutes?.takeIf { it > 0 }
        if (device == null && speed == null && runtime == null) return null
        val base = device ?: PlaybackCapabilitiesPayload()
        return base.copy(speedMbps = speed, runtimeMinutes = runtime)
    }

    private val playbackJson = Json {
        encodeDefaults = false
        explicitNulls = false
    }
}

internal object PlaybackFailureMemory {
    private const val PREFS = "playback_compat_failures"
    private const val KEY = "failures"
    private const val MAX_ENTRIES = 8
    private val json = Json { ignoreUnknownKeys = true }

    fun snapshot(context: Context): List<ObservedPlaybackFailureDto>? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        val parsed = runCatching { json.decodeFromString<List<ObservedPlaybackFailureDto>>(raw) }.getOrNull()
        return parsed?.takeIf { it.isNotEmpty() }
    }

    fun record(context: Context, mime: String?, codecs: String?, height: Int) {
        val codec = failureCodec(mime, codecs) ?: return
        val profile = failureProfile(codecs)
        val dolby = codecs?.startsWith("dvh", ignoreCase = true) == true ||
            mime.equals("video/dolby-vision", ignoreCase = true)
        val bucket = when {
            height >= 2160 -> 2160
            height >= 1080 -> 1080
            height >= 720 -> 720
            height > 0 -> height
            else -> null
        }
        val current = snapshot(context).orEmpty().toMutableList()
        val index = current.indexOfFirst {
            it.codec == codec && it.profile == profile && it.height == bucket && it.dolbyVision == dolby
        }
        if (index >= 0) {
            val existing = current[index]
            current[index] = existing.copy(count = existing.count + 1, mime = mime, codecs = codecs)
        } else {
            current.add(
                ObservedPlaybackFailureDto(
                    codec = codec,
                    profile = profile,
                    height = bucket,
                    mime = mime,
                    codecs = codecs,
                    dolbyVision = dolby,
                ),
            )
        }
        val trimmed = current.takeLast(MAX_ENTRIES)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, json.encodeToString(trimmed))
            .apply()
    }

    private fun failureCodec(mime: String?, codecs: String?): String? {
        val codecsLower = codecs?.lowercase().orEmpty()
        return when {
            mime.equals("video/dolby-vision", true) || codecsLower.startsWith("dvh") -> "hevc"
            mime.equals("video/hevc", true) || codecsLower.startsWith("hvc1") || codecsLower.startsWith("hev1") -> "hevc"
            mime.equals("video/avc", true) || codecsLower.startsWith("avc1") -> "avc"
            mime.equals("video/av01", true) || codecsLower.startsWith("av01") -> "av1"
            mime.equals("video/x-vnd.on2.vp9", true) || codecsLower.startsWith("vp09") -> "vp9"
            else -> null
        }
    }

    private fun failureProfile(codecs: String?): String? {
        val value = codecs?.lowercase().orEmpty()
        return when {
            value.startsWith("dvh") || value.startsWith("hvc1.2") || value.startsWith("hev1.2") -> "Main10"
            value.startsWith("hvc1.1") || value.startsWith("hev1.1") -> "Main"
            else -> null
        }
    }
}

/**
 * Android TV often draws the UI a step below the panel: 4K sets report a 1920x1080 window,
 * and 1080p sets a 1280x720 window. Stream requests need the panel, which is the largest
 * display mode, not that framebuffer.
 */
internal fun choosePanelSize(modeSizes: List<Pair<Int, Int>>): PlaybackScreenDto? {
    val best = modeSizes
        .filter { (width, height) -> width > 0 && height > 0 }
        .maxByOrNull { (width, height) -> width.toLong() * height.toLong() }
        ?: return null
    val (width, height) = best
    return if (width >= height) PlaybackScreenDto(width, height) else PlaybackScreenDto(height, width)
}

internal fun isApachiyAddonBase(baseUrl: String): Boolean {
    val path = baseUrl.substringBefore('?').trimEnd('/')
    return path.endsWith("/apachiy", ignoreCase = true)
}
