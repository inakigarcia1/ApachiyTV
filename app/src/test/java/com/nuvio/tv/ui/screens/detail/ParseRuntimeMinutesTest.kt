package com.nuvio.tv.ui.screens.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseRuntimeMinutesTest {
    @Test
    fun readsMinutesFromTheDetailLabel() {
        assertEquals(136, parseRuntimeMinutes("2h 16m"))
        assertEquals(109, parseRuntimeMinutes("109 min"))
        assertEquals(49, parseRuntimeMinutes("49"))
        assertNull(parseRuntimeMinutes(null))
        assertNull(parseRuntimeMinutes(""))
    }
}
