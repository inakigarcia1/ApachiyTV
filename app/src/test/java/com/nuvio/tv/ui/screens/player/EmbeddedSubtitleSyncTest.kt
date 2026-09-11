package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.ui.screens.player.embedded.AddonSubtitleLoadingGate
import com.nuvio.tv.ui.screens.player.embedded.AddonSubtitleRequest
import com.nuvio.tv.ui.screens.player.embedded.EmbeddedSubtitleCue
import com.nuvio.tv.ui.screens.player.embedded.EmbeddedSubtitleReference
import com.nuvio.tv.ui.screens.player.embedded.EmbeddedTextCodec
import com.nuvio.tv.ui.screens.player.embedded.EmbeddedTextTrack
import com.nuvio.tv.ui.screens.player.embedded.isApachiySubtitleAddon
import com.nuvio.tv.ui.screens.player.embedded.isEmbeddedEnglishLanguage
import com.nuvio.tv.ui.screens.player.embedded.selectEmbeddedReferenceTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedSubtitleSyncTest {
    @Test
    fun englishHelperDetectsVariants() {
        assertTrue(isEmbeddedEnglishLanguage("en"))
        assertTrue(isEmbeddedEnglishLanguage("eng"))
        assertTrue(isEmbeddedEnglishLanguage("en-US"))
        assertTrue(isEmbeddedEnglishLanguage("eng-US"))
        assertTrue(isEmbeddedEnglishLanguage(null, "English"))
        assertFalse(isEmbeddedEnglishLanguage("spa"))
        assertFalse(isEmbeddedEnglishLanguage("es-419"))
    }

    @Test
    fun rankingPrefersEnglishDialogueThenCueCount() {
        val spanish = track("spa", "Spanish", cues = 12)
        val forcedEn = track("eng", "English Forced", forced = true, cues = 8)
        val sdhEn = track("eng", "English SDH", cues = 20)
        val dialogueEn = track("eng", "English", cues = 10)
        val french = track("fra", "French", cues = 40)
        val chosen = selectEmbeddedReferenceTrack(listOf(spanish, forcedEn, sdhEn, dialogueEn, french))
        assertEquals("English", chosen?.name)
        assertEquals("eng", chosen?.language)
    }

    @Test
    fun rankingFallsBackToNonSpanishWhenEnglishMissing() {
        val spanish = track("spa", "Spanish", cues = 30)
        val forcedFr = track("fra", "French Forced", forced = true, cues = 5)
        val dialogueDe = track("deu", "German", cues = 9)
        val chosen = selectEmbeddedReferenceTrack(listOf(spanish, forcedFr, dialogueDe))
        assertEquals("German", chosen?.name)
    }

    @Test
    fun rankingReturnsNullWhenOnlySpanishExists() {
        assertNull(selectEmbeddedReferenceTrack(listOf(track("es", "Español", cues = 4))))
    }

    @Test
    fun postsOnlyToApachiyAddon() {
        val apachiy = addon("com.apachiy.addon", "https://api.example/apachiy")
        val other = addon("org.stremio.subtitles", "https://subs.example")
        val reference = EmbeddedSubtitleReference(
            bytes = "1\n00:00:00,000 --> 00:00:01,000\nHi\n".encodeToByteArray(),
            filename = "embedded.srt",
            language = "eng",
        )
        assertTrue(
            AddonSubtitleRequest.shouldPostEmbeddedReference(
                addon = apachiy,
                subtitleUrl = "https://api.example/apachiy/subtitles/movie/tt1.json",
                reference = reference,
            ),
        )
        assertFalse(
            AddonSubtitleRequest.shouldPostEmbeddedReference(
                addon = other,
                subtitleUrl = "https://subs.example/subtitles/movie/tt1.json",
                reference = reference,
            ),
        )
        assertTrue(isApachiySubtitleAddon(apachiy, "https://api.example/apachiy/subtitles/movie/tt1.json"))
        assertFalse(isApachiySubtitleAddon(other, "https://subs.example/subtitles/movie/tt1.json"))
    }

    @Test
    fun postFallsBackToGetUnless2xx() {
        assertFalse(AddonSubtitleRequest.shouldFallbackPostToGet(200))
        assertTrue(AddonSubtitleRequest.shouldFallbackPostToGet(404))
        assertTrue(AddonSubtitleRequest.shouldFallbackPostToGet(405))
        assertTrue(AddonSubtitleRequest.shouldFallbackPostToGet(415))
        assertTrue(AddonSubtitleRequest.shouldFallbackPostToGet(500))
    }

    @Test
    fun overlayWaitsForPipelineUnlessTimeoutAndKeepsBuffering() {
        assertFalse(
            AddonSubtitleLoadingGate.shouldDismissOpeningOverlay(
                playerIsLoading = false,
                pipelineDone = false,
                elapsedMs = 1_000L,
            ),
        )
        assertTrue(
            AddonSubtitleLoadingGate.shouldDismissOpeningOverlay(
                playerIsLoading = false,
                pipelineDone = true,
                elapsedMs = 1_000L,
            ),
        )
        assertTrue(
            AddonSubtitleLoadingGate.shouldDismissOpeningOverlay(
                playerIsLoading = false,
                pipelineDone = false,
                elapsedMs = AddonSubtitleLoadingGate.TIMEOUT_MS,
            ),
        )
        assertFalse(
            AddonSubtitleLoadingGate.shouldDismissOpeningOverlay(
                playerIsLoading = true,
                pipelineDone = true,
                elapsedMs = AddonSubtitleLoadingGate.TIMEOUT_MS,
            ),
        )
    }

    private fun track(
        language: String?,
        name: String?,
        forced: Boolean = false,
        cues: Int,
    ): EmbeddedTextTrack = EmbeddedTextTrack(
        language = language,
        name = name,
        forced = forced,
        codec = EmbeddedTextCodec.SubRip,
        cues = List(cues) { index ->
            EmbeddedSubtitleCue(
                startMs = index * 1_000L,
                endMs = index * 1_000L + 800L,
                text = "line $index",
            )
        },
    )

    private fun addon(id: String, baseUrl: String): Addon = Addon(
        id = id,
        name = id,
        version = "1.0.0",
        description = null,
        logo = null,
        baseUrl = baseUrl,
        catalogs = emptyList(),
        types = emptyList(),
        resources = emptyList(),
    )
}
