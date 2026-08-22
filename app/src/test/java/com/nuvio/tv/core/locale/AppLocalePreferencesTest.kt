package com.nuvio.tv.core.locale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLocalePreferencesTest {

    @Test
    fun `unset preference defaults to Spanish`() {
        assertEquals("es", AppLocalePreferences.effectiveLocaleTag(null))
    }

    @Test
    fun `empty preference follows system locale`() {
        assertNull(AppLocalePreferences.effectiveLocaleTag(""))
    }

    @Test
    fun `explicit preference is preserved`() {
        assertEquals("en", AppLocalePreferences.effectiveLocaleTag("en"))
    }
}
