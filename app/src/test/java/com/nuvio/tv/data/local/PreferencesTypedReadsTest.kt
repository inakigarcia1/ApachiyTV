package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreferencesTypedReadsTest {
    private val intKey = intPreferencesKey("card_depth_edge_strength")
    private val stringKey = stringPreferencesKey("card_depth_edge_strength")

    @Test
    fun coerceToInt_acceptsNumericStrings() {
        assertEquals(75, coerceToInt("75"))
        assertEquals(50, coerceToInt("50.0"))
        assertEquals(12, coerceToInt(12))
        assertEquals(3, coerceToInt(3L))
        assertNull(coerceToInt("modern"))
    }

    @Test
    fun coerceToBoolean_acceptsStringFlags() {
        assertEquals(true, coerceToBoolean("true"))
        assertEquals(false, coerceToBoolean("0"))
        assertEquals(true, coerceToBoolean(1))
        assertNull(coerceToBoolean("maybe"))
    }

    @Test
    fun intOrNull_readsStringStoredUnderSameKeyName() {
        val prefs = emptyPreferences().toMutablePreferences().apply {
            this[stringKey] = "42"
        }.toPreferences()

        assertEquals(42, prefs.intOrNull(intKey))
        assertEquals(42, prefs.intOrDefault(intKey, 10))
    }

    @Test
    fun intOrNull_returnsDefaultWhenValueIsNotNumeric() {
        val prefs = emptyPreferences().toMutablePreferences().apply {
            this[stringKey] = "wide"
        }.toPreferences()

        assertNull(prefs.intOrNull(intKey))
        assertEquals(10, prefs.intOrDefault(intKey, 10))
    }
}
