package com.nuvio.tv.core.playback

import android.content.Context
import android.media.MediaCodecInfo
import android.os.Build
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
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
data class PlaybackCapabilitiesPayload(
    val screen: PlaybackScreenDto? = null,
    val playerBackend: String? = null,
    val codecs: List<String>? = null,
    @SerialName("decoderCapabilities")
    val decoderCapabilities: Map<String, Map<String, Boolean>>? = null,
    val speedMbps: Double? = null,
    val runtimeMinutes: Int? = null,
) {
    fun hasAnyConstraint(): Boolean =
        (screen != null && screen.width > 0 && screen.height > 0)
            || !codecs.isNullOrEmpty()
            || !decoderCapabilities.isNullOrEmpty()
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
        if (!baseUrl.contains("/apachiy/", ignoreCase = true)) return streamUrl
        val payload = playbackPayloadForStream(runtimeMinutes)?.takeIf { it.hasAnyConstraint() } ?: return streamUrl
        val json = playbackJson.encodeToString(payload)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(Charsets.UTF_8))
        val separator = if (streamUrl.contains('?')) '&' else '?'
        return "$streamUrl$separator" + "playback=$encoded"
    }

    @OptIn(UnstableApi::class)
    private fun probe(context: Context): PlaybackCapabilitiesPayload? {
        val screen = readPhysicalScreen(context) ?: return null
        val decoderCapabilities = buildDecoderMatrix()
        if (decoderCapabilities.isEmpty()) {
            return PlaybackCapabilitiesPayload(screen = screen)
        }
        return PlaybackCapabilitiesPayload(
            screen = screen,
            decoderCapabilities = decoderCapabilities,
        )
    }

    private fun readPhysicalScreen(context: Context): PlaybackScreenDto? {
        val wm = context.getSystemService(WindowManager::class.java) ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            return PlaybackScreenDto(bounds.width(), bounds.height()).takeIf {
                it.width > 0 && it.height > 0
            }
        }
        @Suppress("DEPRECATION")
        val display = wm.defaultDisplay ?: return null
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        return PlaybackScreenDto(metrics.widthPixels, metrics.heightPixels).takeIf {
            it.width > 0 && it.height > 0
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildDecoderMatrix(): Map<String, Map<String, Boolean>> {
        val matrix = mutableMapOf<String, MutableMap<String, Boolean>>()
        for ((mimeType, wireName) in MIME_WIRE_NAMES) {
            val decoders = MediaCodecUtil.getDecoderInfos(mimeType, false, false)
            val cells = mutableMapOf<String, Boolean>()
            for (decoder in decoders) {
                if (!decoder.hardwareAccelerated || decoder.softwareOnly) continue
                val videoCaps = decoder.capabilities?.videoCapabilities ?: continue
                for (cell in PROBE_CELLS) {
                    if (cells[cell.key] == true) continue
                    if (!supports(videoCaps, cell.width, cell.height, cell.fps)) continue
                    cells[cell.key] = true
                }
            }
            if (cells.isNotEmpty()) {
                matrix[wireName] = cells
            }
        }
        return matrix
    }

    private fun supports(
        videoCaps: MediaCodecInfo.VideoCapabilities,
        width: Int,
        height: Int,
        fps: Int,
    ): Boolean {
        if (!videoCaps.isSizeSupported(width, height)) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            videoCaps.areSizeAndRateSupported(width, height, fps.toDouble())
        } else {
            @Suppress("DEPRECATION")
            videoCaps.isSizeSupported(width, height)
        }
    }

    private data class ProbeCell(val key: String, val width: Int, val height: Int, val fps: Int)

    private val PROBE_CELLS = listOf(
        ProbeCell("720p30", 1280, 720, 30),
        ProbeCell("720p60", 1280, 720, 60),
        ProbeCell("1080p30", 1920, 1080, 30),
        ProbeCell("1080p60", 1920, 1080, 60),
        ProbeCell("1440p30", 2560, 1440, 30),
        ProbeCell("1440p60", 2560, 1440, 60),
        ProbeCell("2160p24", 3840, 2160, 24),
        ProbeCell("2160p30", 3840, 2160, 30),
        ProbeCell("2160p60", 3840, 2160, 60),
    )

    private val MIME_WIRE_NAMES = listOf(
        MimeTypes.VIDEO_H264 to "video/avc",
        MimeTypes.VIDEO_H265 to "video/hevc",
        MimeTypes.VIDEO_VP9 to "video/x-vnd.on2.vp9",
        MimeTypes.VIDEO_AV1 to "video/av01",
    )

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
