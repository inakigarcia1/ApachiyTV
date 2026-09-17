package com.nuvio.tv.data.local

import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal fun coerceToInt(raw: Any?): Int? = when (raw) {
    null -> null
    is Int -> raw
    is Long -> raw.toInt()
    is Float -> raw.toInt()
    is Double -> raw.toInt()
    is Boolean -> if (raw) 1 else 0
    is String -> raw.trim().toIntOrNull() ?: raw.trim().toDoubleOrNull()?.toInt()
    else -> null
}

internal fun coerceToBoolean(raw: Any?): Boolean? = when (raw) {
    null -> null
    is Boolean -> raw
    is String -> when (raw.trim().lowercase()) {
        "true", "1" -> true
        "false", "0" -> false
        else -> null
    }
    is Int -> raw != 0
    is Long -> raw != 0L
    else -> null
}

internal fun coerceToString(raw: Any?): String? = when (raw) {
    null -> null
    is String -> raw
    is Set<*> -> null
    else -> raw.toString()
}

internal fun coerceToFloat(raw: Any?): Float? = when (raw) {
    null -> null
    is Float -> raw
    is Double -> raw.toFloat()
    is Int -> raw.toFloat()
    is Long -> raw.toFloat()
    is String -> raw.trim().toFloatOrNull()
    else -> null
}

internal fun Preferences.rawOrNull(key: Preferences.Key<*>): Any? = asMap()[key]

internal fun Preferences.intOrNull(key: Preferences.Key<Int>): Int? = coerceToInt(rawOrNull(key))

internal fun Preferences.booleanOrNull(key: Preferences.Key<Boolean>): Boolean? =
    coerceToBoolean(rawOrNull(key))

internal fun Preferences.stringOrNull(key: Preferences.Key<String>): String? =
    coerceToString(rawOrNull(key))

internal fun Preferences.floatOrNull(key: Preferences.Key<Float>): Float? =
    coerceToFloat(rawOrNull(key))

internal fun Preferences.intOrDefault(key: Preferences.Key<Int>, default: Int): Int =
    intOrNull(key) ?: default

internal fun Preferences.booleanOrDefault(key: Preferences.Key<Boolean>, default: Boolean): Boolean =
    booleanOrNull(key) ?: default

internal fun <T> Flow<Preferences>.mapPreferencesSafely(
    tag: String,
    extract: (Preferences) -> T
): Flow<T> = map { prefs ->
    try {
        extract(prefs)
    } catch (e: ClassCastException) {
        Log.e(tag, "Preference type mismatch; falling back to defaults", e)
        extract(emptyPreferences())
    }
}
