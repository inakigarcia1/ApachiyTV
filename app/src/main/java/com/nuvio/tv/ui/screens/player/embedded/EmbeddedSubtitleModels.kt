package com.nuvio.tv.ui.screens.player.embedded

data class EmbeddedSubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

data class EmbeddedTextTrack(
    val language: String?,
    val name: String?,
    val forced: Boolean,
    val codec: EmbeddedTextCodec,
    val cues: List<EmbeddedSubtitleCue>,
    val assHeader: String? = null,
)

enum class EmbeddedTextCodec {
    SubRip,
    Ass,
    Ssa,
    WebVtt,
}

data class EmbeddedSubtitleReference(
    val bytes: ByteArray,
    val filename: String,
    val language: String?,
)

data class EmbeddedExtractResult(
    val hasEmbeddedSpanish: Boolean,
    val reference: EmbeddedSubtitleReference?,
)

internal object AddonSubtitleLoadingGate {
    const val TIMEOUT_MS = 20_000L

    fun shouldDismissOpeningOverlay(
        playerIsLoading: Boolean,
        pipelineDone: Boolean,
        elapsedMs: Long,
    ): Boolean {
        // Playback starts when the first frame is ready. Subtitle fetch and ffsubsync
        // keep running behind that; the overlay no longer waits for them.
        return !playerIsLoading
    }
}
