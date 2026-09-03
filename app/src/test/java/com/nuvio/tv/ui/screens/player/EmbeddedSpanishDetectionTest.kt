package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedSpanishDetectionTest {
    @Test
    fun detectsSpanishEmbeddedTrackVariants() {
        assertTrue(
            PlayerSubtitleUtils.isEmbeddedSpanishTrack(
                TrackInfo(index = 0, name = "Spanish", language = "spa"),
            ),
        )
        assertTrue(
            PlayerSubtitleUtils.isEmbeddedSpanishTrack(
                TrackInfo(index = 1, name = "Español (Latino)", language = "es-419"),
            ),
        )
        assertFalse(
            PlayerSubtitleUtils.isEmbeddedSpanishTrack(
                TrackInfo(index = 2, name = "English", language = "eng"),
            ),
        )
    }
}
