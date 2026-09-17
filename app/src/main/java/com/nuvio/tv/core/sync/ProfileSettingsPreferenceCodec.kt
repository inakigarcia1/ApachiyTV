package com.nuvio.tv.core.sync

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal val profileSettingsIntKeys = setOf(
    "focused_poster_backdrop_expand_delay_seconds",
    "poster_card_width_dp",
    "poster_card_height_dp",
    "poster_card_corner_radius_dp",
    "card_depth_edge_strength",
    "card_depth_sheen_strength",
    "card_depth_edge_coverage",
    "trailer_delay_seconds",
    "continue_watching_days_cap",
    "instant_playback_preparation_limit",
    "stream_max_results",
    "decoder_priority",
    "audio_amplification_db",
    "center_mix_level_db",
    "dv7_libdovi_mode_override",
    "stream_auto_play_timeout_seconds",
    "still_watching_episode_threshold",
    "next_episode_threshold_percent",
    "next_episode_threshold_minutes_before_end",
    "stream_reuse_last_link_cache_hours",
    "vod_cache_size_mb",
    "parallel_connection_count",
    "parallel_chunk_size_mb",
    "parallel_chunk_size_kb",
    "resize_mode",
    "subtitle_size",
    "subtitle_vertical_offset",
    "subtitle_text_color",
    "subtitle_background_color",
    "subtitle_outline_color",
    "subtitle_outline_width",
    "min_buffer_ms",
    "max_buffer_ms",
    "buffer_for_playback_ms",
    "buffer_for_playback_after_rebuffer_ms",
    "target_buffer_size_mb",
    "back_buffer_duration_ms"
)

internal val profileSettingsFloatKeys = setOf(
    "next_episode_threshold_percent_v2",
    "next_episode_threshold_minutes_before_end_v2"
)

internal fun applyEncodedPreference(
    mutablePrefs: MutablePreferences,
    keyName: String,
    encodedValue: JsonElement
) {
    val obj = encodedValue as? JsonObject ?: return
    val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return
    val value = obj["value"] ?: JsonNull
    val primitive = runCatching { value.jsonPrimitive }.getOrNull()

    when (type) {
        "string" -> {
            val parsed = primitive?.contentOrNull ?: return
            when {
                keyName in profileSettingsIntKeys -> {
                    val asInt = parsed.toIntOrNull() ?: parsed.toDoubleOrNull()?.toInt()
                    if (asInt != null) {
                        mutablePrefs[intPreferencesKey(keyName)] = asInt
                    } else {
                        mutablePrefs[stringPreferencesKey(keyName)] = parsed
                    }
                }
                keyName in profileSettingsFloatKeys -> {
                    val asFloat = parsed.toFloatOrNull()
                    if (asFloat != null) {
                        mutablePrefs[floatPreferencesKey(keyName)] = asFloat
                    } else {
                        mutablePrefs[stringPreferencesKey(keyName)] = parsed
                    }
                }
                else -> mutablePrefs[stringPreferencesKey(keyName)] = parsed
            }
        }
        "boolean" -> {
            val parsed = primitive?.contentOrNull?.toBooleanStrictOrNull() ?: return
            mutablePrefs[booleanPreferencesKey(keyName)] = parsed
        }
        "int" -> {
            val parsed = primitive?.intOrNull
                ?: primitive?.contentOrNull?.toIntOrNull()
                ?: primitive?.doubleOrNull?.toInt()
                ?: return
            mutablePrefs[intPreferencesKey(keyName)] = parsed
        }
        "long" -> {
            val parsed = primitive?.longOrNull
                ?: primitive?.contentOrNull?.toLongOrNull()
                ?: primitive?.doubleOrNull?.toLong()
                ?: return
            mutablePrefs[longPreferencesKey(keyName)] = parsed
        }
        "float" -> {
            val parsed = primitive?.floatOrNull
                ?: primitive?.contentOrNull?.toFloatOrNull()
                ?: primitive?.doubleOrNull?.toFloat()
                ?: return
            mutablePrefs[floatPreferencesKey(keyName)] = parsed
        }
        "double" -> {
            val parsed = primitive?.doubleOrNull
                ?: primitive?.contentOrNull?.toDoubleOrNull()
                ?: return
            mutablePrefs[doublePreferencesKey(keyName)] = parsed
        }
        "string_set" -> {
            val parsed = value.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }.toSet()
            mutablePrefs[stringSetPreferencesKey(keyName)] = parsed
        }
    }
}
