package com.nuvio.tv.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChoosePanelSizeTest {
    @Test
    fun usesLargestModeWhenUiIsScaledDown() {
        val fourK = choosePanelSize(listOf(1920 to 1080, 3840 to 2160))
        assertEquals(PlaybackScreenDto(3840, 2160), fourK)

        val fullHd = choosePanelSize(listOf(1280 to 720, 1920 to 1080))
        assertEquals(PlaybackScreenDto(1920, 1080), fullHd)
    }

    @Test
    fun ignoresEmptyModes() {
        assertNull(choosePanelSize(emptyList()))
        assertNull(choosePanelSize(listOf(0 to 0)))
    }
}
