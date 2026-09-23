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
        commentary: Boolean = false
    ) = AudioTrackCandidate(index, language, name, commentary)

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
