package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.domain.model.Subtitle

private const val UnknownLanguageKey = "__unknown__"

/**
 * Display title for embedded (container) subtitle tracks in the Integrado rail.
 * Uses the track language name instead of container labels such as release-group URLs.
 */
fun internalSubtitleRailTitle(language: String?, languageKey: String): String {
    val normalizedLanguage = language
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { PlayerSubtitleUtils.normalizeLanguageCode(it) }

    if (!normalizedLanguage.isNullOrBlank() && normalizedLanguage != UnknownLanguageKey) {
        return Subtitle.languageCodeToName(normalizedLanguage)
    }

    if (languageKey.isNotBlank() && languageKey != UnknownLanguageKey) {
        return Subtitle.languageCodeToName(languageKey)
    }

    return Subtitle.languageCodeToName("und")
}
