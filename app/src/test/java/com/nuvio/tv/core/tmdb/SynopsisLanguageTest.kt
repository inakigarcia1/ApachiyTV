package com.nuvio.tv.core.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SynopsisLanguageTest {
    @Test
    fun keepsSpanishAddonTextWhenTmdbFallsBackToEnglish() {
        assertFalse("Two imprisoned men bond over a number of years, finding solace through acts of decency.".isSpanishSynopsis())
        assertTrue("La historia de un hombre que vuelve a casa.".isSpanishSynopsis())
        assertEquals(
            "La historia de un hombre que vuelve a casa.",
            chooseSynopsis(
                incoming = "The story of a man who comes back home after the war.",
                current = "La historia de un hombre que vuelve a casa.",
            ),
        )
    }
}
