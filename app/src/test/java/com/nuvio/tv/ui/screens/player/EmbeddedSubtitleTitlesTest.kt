package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.ui.util.languageCodeToName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EmbeddedSubtitleTitlesTest {

    @Test
    fun `uses language name instead of container label`() {
        val title = internalSubtitleRailTitle(
            language = "es",
            languageKey = "es"
        )

        assertEquals(languageCodeToName("es"), title)
        assertFalse(title.contains("hackstore", ignoreCase = true))
    }

    @Test
    fun `falls back to language key when track language is missing`() {
        val title = internalSubtitleRailTitle(
            language = null,
            languageKey = "es"
        )

        assertEquals(languageCodeToName("es"), title)
    }

    @Test
    fun `does not surface container labels such as hackstore urls`() {
        val title = internalSubtitleRailTitle(
            language = "es",
            languageKey = "es"
        )

        assertFalse(title.contains("www.hackstore", ignoreCase = true))
        assertFalse(title.contains("Forzados", ignoreCase = true))
    }
}
