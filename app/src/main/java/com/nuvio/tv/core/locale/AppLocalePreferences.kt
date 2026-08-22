package com.nuvio.tv.core.locale

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object AppLocalePreferences {
    const val PREFS_NAME = "app_locale"
    const val KEY_LOCALE_TAG = "locale_tag"
    const val DEFAULT_LOCALE_TAG = "es"

    fun readStoredLocaleTag(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LOCALE_TAG, null)
    }

    /** Effective BCP-47 tag to apply. `null` means follow the device system locale. */
    fun effectiveLocaleTag(stored: String?): String? = when (stored) {
        null -> DEFAULT_LOCALE_TAG
        "" -> null
        else -> stored
    }

    fun effectiveLocaleTag(context: Context): String? =
        effectiveLocaleTag(readStoredLocaleTag(context))

    fun ensureDefaultLocaleIfUnset(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_LOCALE_TAG)) {
            prefs.edit().putString(KEY_LOCALE_TAG, DEFAULT_LOCALE_TAG).apply()
        }
    }

    /** Value stored in [com.nuvio.tv.LocaleCache]: empty string = system locale. */
    fun cacheLocaleTag(context: Context): String = effectiveLocaleTag(context).orEmpty()

    /** Selected option in the language picker: `null` = system. */
    fun selectedLocaleTagForUi(context: Context): String? {
        return when (val stored = readStoredLocaleTag(context)) {
            null -> DEFAULT_LOCALE_TAG
            "" -> null
            else -> stored
        }
    }

    fun createLocalizedContext(base: Context): Context {
        val tag = effectiveLocaleTag(base) ?: return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(config)
    }
}
