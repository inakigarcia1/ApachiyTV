package com.nuvio.tv.core.sync

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileSettingsPreferenceCodecTest {
    @Test
    fun applyEncodedPreference_coercesStringTypedIntKeysToInt() {
        val prefs = emptyPreferences().toMutablePreferences()

        applyEncodedPreference(
            prefs,
            "card_depth_edge_strength",
            buildJsonObject {
                put("type", "string")
                put("value", "75")
            }
        )

        assertEquals(75, prefs[intPreferencesKey("card_depth_edge_strength")])
        assertEquals(75, prefs.asMap()[intPreferencesKey("card_depth_edge_strength")])
    }

    @Test
    fun applyEncodedPreference_parsesIntTypeWhenJsonValueIsString() {
        val prefs = emptyPreferences().toMutablePreferences()

        applyEncodedPreference(
            prefs,
            "poster_card_width_dp",
            buildJsonObject {
                put("type", "int")
                put("value", "160")
            }
        )

        assertEquals(160, prefs[intPreferencesKey("poster_card_width_dp")])
    }

    @Test
    fun applyEncodedPreference_keepsNonNumericStringsAsStrings() {
        val prefs = emptyPreferences().toMutablePreferences()

        applyEncodedPreference(
            prefs,
            "selected_layout",
            buildJsonObject {
                put("type", "string")
                put("value", "MODERN")
            }
        )

        assertEquals("MODERN", prefs[stringPreferencesKey("selected_layout")])
    }

    @Test
    fun applyEncodedPreference_coercesStringTypedFloatKeys() {
        val prefs = emptyPreferences().toMutablePreferences()

        applyEncodedPreference(
            prefs,
            "next_episode_threshold_percent_v2",
            buildJsonObject {
                put("type", "string")
                put("value", "90.5")
            }
        )

        assertEquals(90.5f, prefs[floatPreferencesKey("next_episode_threshold_percent_v2")])
    }

    @Test
    fun applyEncodedPreference_acceptsJsonNumberForIntType() {
        val prefs = emptyPreferences().toMutablePreferences()

        applyEncodedPreference(
            prefs,
            "trailer_delay_seconds",
            buildJsonObject {
                put("type", "int")
                put("value", JsonPrimitive(7))
            }
        )

        assertEquals(7, prefs[intPreferencesKey("trailer_delay_seconds")])
    }
}
