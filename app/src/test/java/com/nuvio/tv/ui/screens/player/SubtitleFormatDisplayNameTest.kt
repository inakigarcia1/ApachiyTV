package com.nuvio.tv.ui.screens.player

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleFormatDisplayNameTest {

    @Test
    fun mapsCommonMpvAndMatroskaCodecHints() {
        assertEquals("SRT", CustomDefaultTrackNameProvider.subtitleFormatDisplayName(null, "subrip"))
        assertEquals("PGS", CustomDefaultTrackNameProvider.subtitleFormatDisplayName(null, "hdmv_pgs_subtitle"))
        assertEquals(
            "ASS",
            CustomDefaultTrackNameProvider.subtitleFormatDisplayName(null, "s_text/ass"),
        )
    }

    @Test
    fun prefersMimeWhenPresent() {
        assertEquals(
            "SRT",
            CustomDefaultTrackNameProvider.subtitleFormatDisplayName(MimeTypes.APPLICATION_SUBRIP, "subrip"),
        )
    }

}
