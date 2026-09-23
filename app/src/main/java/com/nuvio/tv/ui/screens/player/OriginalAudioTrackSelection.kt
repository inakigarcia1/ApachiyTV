package com.nuvio.tv.ui.screens.player

import androidx.media3.common.C
import com.nuvio.tv.data.local.AudioLanguageOption

data class AudioTrackCandidate(
    val index: Int,
    val language: String?,
    val name: String,
    val isCommentary: Boolean
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

fun pickPreferredAudioTrackIndex(
    tracks: List<AudioTrackCandidate>,
    originalLanguage: String?,
    secondaryLanguage: String?,
    deviceLanguages: List<String>,
    preferredAudioLanguage: String
): Int? {
    if (tracks.isEmpty() || !shouldUseOriginalAudioHeuristic(preferredAudioLanguage)) return null

    val nonCommentary = tracks.filter { !it.isCommentary }
    val pool = nonCommentary.ifEmpty { tracks }

    val original = originalLanguage?.trim()?.takeIf { it.isNotBlank() }
    if (original != null) {
        pool.firstOrNull { audioLanguagesMatch(it.language, original) }?.index?.let { return it }
    }

    secondaryLanguage?.trim()?.takeIf { it.isNotBlank() }?.let { secondary ->
        pool.firstOrNull { audioLanguagesMatch(it.language, secondary) }?.index?.let { return it }
    }

    for (deviceLanguage in deviceLanguages) {
        pool.firstOrNull { audioLanguagesMatch(it.language, deviceLanguage) }?.index?.let { return it }
    }

    return pool.firstOrNull()?.index
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
