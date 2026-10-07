package com.nuvio.tv.ui.screens.player

import androidx.media3.common.C
import com.nuvio.tv.data.local.AudioLanguageOption

data class AudioTrackCandidate(
    val index: Int,
    val language: String?,
    val name: String,
    val isCommentary: Boolean,
    val codec: String? = null,
    val isSupported: Boolean = true,
)

fun shouldUseOriginalAudioHeuristic(preferredAudioLanguage: String): Boolean {
    return when (preferredAudioLanguage.trim().lowercase()) {
        AudioLanguageOption.DEFAULT,
        AudioLanguageOption.DEVICE,
        AudioLanguageOption.ORIGINAL -> true
        else -> false
    }
}

fun isAudioCommentaryTrack(name: String, roleFlags: Int? = null): Boolean {
    if (roleFlags != null && (roleFlags and C.ROLE_FLAG_COMMENTARY) != 0) return true
    val normalized = name.trim().lowercase()
    if (normalized.contains("director's cut") || normalized.contains("directors cut")) {
        return false
    }
    return AUDIO_COMMENTARY_HINTS.any { normalized.contains(it) }
}

fun audioLanguagesMatch(first: String?, second: String?): Boolean {
    if (first.isNullOrBlank() || second.isNullOrBlank()) return false
    val left = expandLanguageTokens(first)
    val right = expandLanguageTokens(second)
    return left.intersect(right).isNotEmpty()
}

fun audioCodecKey(codec: String?, name: String): String? {
    val text = "${codec.orEmpty()} $name".lowercase()
    return when {
        "truehd" in text || "true hd" in text || "mlp" in text -> "truehd"
        "atmos" in text -> "atmos"
        "dts-hd" in text || "dts hd" in text || "dtshd" in text -> "dtshd"
        "dts:x" in text || "dtsx" in text -> "dtsx"
        "e-ac-3" in text || "eac3" in text || "ec-3" in text || "dd+" in text -> "eac3"
        "ac-3" in text || "ac3" in text || "dolby digital" in text -> "ac3"
        "flac" in text -> "flac"
        "opus" in text -> "opus"
        "vorbis" in text -> "vorbis"
        "aac" in text -> "aac"
        "mp3" in text -> "mp3"
        "dts" in text -> "dts"
        else -> null
    }
}

// ponytail: static list from app/libs/README.md ENABLED_DECODERS. Update it if the ffmpeg AAR drops a decoder.
val SOFTWARE_AUDIO_CODECS = setOf(
    "aac", "mp3", "opus", "vorbis", "flac", "ac3", "eac3", "dts", "truehd",
)

fun withSoftwareAudioCodecs(deviceSupported: Set<String>): Set<String> =
    deviceSupported + SOFTWARE_AUDIO_CODECS

private val GENERIC_AUDIO_COMPATIBILITY = listOf(
    "aac", "mp3", "opus", "vorbis", "ac3", "eac3", "flac", "dts", "dtsx", "dtshd", "truehd", "atmos",
)

fun genericAudioCompatibilityRank(codec: String?, name: String): Int {
    val key = audioCodecKey(codec, name) ?: return GENERIC_AUDIO_COMPATIBILITY.size
    val index = GENERIC_AUDIO_COMPATIBILITY.indexOf(key)
    return if (index >= 0) index else GENERIC_AUDIO_COMPATIBILITY.size
}

fun audioTrackKnownIncompatible(
    codec: String?,
    name: String,
    supportedAudioCodecs: Set<String>?,
): Boolean {
    if (supportedAudioCodecs == null) return false
    val key = audioCodecKey(codec, name) ?: return false
    if (key in supportedAudioCodecs) return false
    // DTS-HD and DTS:X decode through the DTS decoder already in the FFmpeg build.
    return !((key == "dtshd" || key == "dtsx") && "dts" in supportedAudioCodecs)
}

fun noPlayableAudioTrack(
    tracks: List<AudioTrackCandidate>,
    supportedAudioCodecs: Set<String>?,
): Boolean {
    if (supportedAudioCodecs == null || tracks.isEmpty()) return false
    val pool = tracks.filter { !it.isCommentary }.ifEmpty { tracks }
    return pool.all {
        !it.isSupported || audioTrackKnownIncompatible(it.codec, it.name, supportedAudioCodecs)
    }
}

fun pickPreferredAudioTrackIndex(
    tracks: List<AudioTrackCandidate>,
    originalLanguage: String?,
    secondaryLanguage: String?,
    deviceLanguages: List<String>,
    preferredAudioLanguage: String,
    supportedAudioCodecs: Set<String>? = null,
): Int? {
    if (tracks.isEmpty() || !shouldUseOriginalAudioHeuristic(preferredAudioLanguage)) return null

    val nonCommentary = tracks.filter { !it.isCommentary }
    val pool = nonCommentary.ifEmpty { tracks }
    val playable = if (supportedAudioCodecs == null) {
        pool
    } else {
        pool.filter { !audioTrackKnownIncompatible(it.codec, it.name, supportedAudioCodecs) }
    }.filter { it.isSupported }
    if (playable.isEmpty()) return null

    fun best(candidates: List<AudioTrackCandidate>): Int? =
        candidates.minWithOrNull(
            compareBy<AudioTrackCandidate>({ genericAudioCompatibilityRank(it.codec, it.name) }, { it.index })
        )?.index

    val original = originalLanguage?.trim()?.takeIf { it.isNotBlank() }
    if (original != null) {
        val matches = playable.filter { audioLanguagesMatch(it.language, original) }
        if (matches.isNotEmpty()) return best(matches)
    }

    secondaryLanguage?.trim()?.takeIf { it.isNotBlank() }?.let { secondary ->
        val matches = playable.filter { audioLanguagesMatch(it.language, secondary) }
        if (matches.isNotEmpty()) return best(matches)
    }

    for (deviceLanguage in deviceLanguages) {
        val matches = playable.filter { audioLanguagesMatch(it.language, deviceLanguage) }
        if (matches.isNotEmpty()) return best(matches)
    }

    return best(playable)
}

private val AUDIO_COMMENTARY_HINTS = listOf(
    "audio commentary",
    "commentary",
    "comentarios",
    "commentaire",
    "commento",
    "director's commentary",
    "director commentary",
    "producer's commentary",
    "producer commentary"
)

private val ISO2_TO_ISO3 = mapOf(
    "en" to "eng",
    "es" to "spa",
    "fr" to "fra",
    "de" to "deu",
    "it" to "ita",
    "pt" to "por",
    "ja" to "jpn",
    "ko" to "kor",
    "zh" to "zho",
    "ru" to "rus",
    "ar" to "ara",
    "hi" to "hin"
)

private fun expandLanguageTokens(raw: String): Set<String> {
    val normalized = raw.trim().lowercase().replace('_', '-')
    val primary = normalized.substringBefore('-')
    val tokens = mutableSetOf(primary, normalized)
    ISO2_TO_ISO3[primary]?.let { tokens += it }
    ISO2_TO_ISO3.entries.firstOrNull { it.value == primary }?.key?.let { tokens += it }
    return tokens
}
