package com.nuvio.tv.ui.screens.player

import androidx.media3.common.C
import com.nuvio.tv.data.local.AudioLanguageOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginalAudioTrackSelectionTest {

    private fun track(
        index: Int,
        language: String?,
        name: String,
        commentary: Boolean = false,
        codec: String? = null,
    ) = AudioTrackCandidate(index, language, name, commentary, codec)

    @Test
    fun picksOriginalOverDub() {
        val tracks = listOf(
            track(0, "spa", "Spanish"),
            track(1, "eng", "English")
        )
        assertEquals(
            1,
            pickPreferredAudioTrackIndex(
                tracks = tracks,
                originalLanguage = "en",
                secondaryLanguage = null,
                deviceLanguages = emptyList(),
                preferredAudioLanguage = AudioLanguageOption.DEVICE
            )
        )
    }

    @Test
    fun skipsCommentaryWhenOriginalExists() {
        val tracks = listOf(
            track(0, "eng", "English Commentary", commentary = true),
            track(1, "eng", "English TrueHD")
        )
        assertEquals(
            1,
            pickPreferredAudioTrackIndex(
                tracks = tracks,
                originalLanguage = "en",
                secondaryLanguage = null,
                deviceLanguages = emptyList(),
                preferredAudioLanguage = AudioLanguageOption.ORIGINAL
            )
        )
    }

    @Test
    fun commentaryDetectedFromRoleFlag() {
        assertTrue(isAudioCommentaryTrack("Main", C.ROLE_FLAG_COMMENTARY))
    }

    @Test
    fun directorsCutIsNotCommentary() {
        assertFalse(isAudioCommentaryTrack("Director's Cut", null))
    }

    @Test
    fun matchesEngAndEn() {
        assertTrue(audioLanguagesMatch("eng", "en-US"))
    }

    @Test
    fun originalLanguagePrefersCompatibleCodecOverEnglishTrueHd() {
        val tracks = listOf(
            track(0, "en", "TrueHD 5.1 (TrueHD 5.1)", codec = "TrueHD"),
            track(1, "ja", "FLAC 2.0 (FLAC Stereo)", codec = "FLAC"),
            track(2, "en", "AC-3 5.1 (AC-3 5.1)", codec = "AC-3"),
        )
        assertEquals(
            1,
            pickPreferredAudioTrackIndex(
                tracks = tracks,
                originalLanguage = "ja",
                secondaryLanguage = null,
                deviceLanguages = listOf("es"),
                preferredAudioLanguage = AudioLanguageOption.ORIGINAL,
                supportedAudioCodecs = setOf("aac", "ac3", "flac", "opus"),
            )
        )
    }

    @Test
    fun softwareDecoderKeepsAc3WhenMediaCodecLacksIt() {
        val tracks = listOf(
            track(0, "und", "Audio 1 (AC-3 Stereo)", codec = "AC-3"),
            track(1, "it", "Italiano (E-AC-3 Stereo)", codec = "E-AC-3"),
            track(2, "ja", "Japonés (E-AC-3 Stereo)", codec = "E-AC-3"),
        )
        val supported = withSoftwareAudioCodecs(setOf("aac", "opus", "flac", "vorbis", "mp3"))
        assertFalse(noPlayableAudioTrack(tracks, supported))
    }

    @Test
    fun knownUnsupportedOnlyTrackNeedsAnotherStream() {
        val tracks = listOf(track(0, "ja", "TrueHD 5.1", codec = "TrueHD"))
        assertTrue(
            noPlayableAudioTrack(tracks, setOf("aac", "ac3", "flac"))
        )
    }

    @Test
    fun missingCapabilityInfoKeepsOriginalLanguageAndMoreCompatibleCodec() {
        val tracks = listOf(
            track(0, "ja", "TrueHD 5.1", codec = "TrueHD"),
            track(1, "ja", "FLAC 2.0", codec = "FLAC"),
        )
        assertEquals(
            1,
            pickPreferredAudioTrackIndex(
                tracks = tracks,
                originalLanguage = "ja",
                secondaryLanguage = null,
                deviceLanguages = emptyList(),
                preferredAudioLanguage = AudioLanguageOption.ORIGINAL,
                supportedAudioCodecs = null,
            )
        )
        assertFalse(noPlayableAudioTrack(tracks, null))
    }

    @Test
    fun concreteLanguageSettingSkipsHeuristic() {
        val tracks = listOf(track(0, "spa", "Spanish"))
        assertEquals(
            null,
            pickPreferredAudioTrackIndex(
                tracks = tracks,
                originalLanguage = "en",
                secondaryLanguage = null,
                deviceLanguages = emptyList(),
                preferredAudioLanguage = "es"
            )
        )
    }
}
