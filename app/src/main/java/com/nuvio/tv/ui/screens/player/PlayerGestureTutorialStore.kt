package com.nuvio.tv.ui.screens.player

import android.content.Context

internal object PlayerGestureTutorialStore {
    private const val preferencesName = "nuvio_player_gesture_tutorial"
    private const val seenKey = "seen"

    fun hasSeen(context: Context): Boolean =
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .getBoolean(seenKey, false)

    fun markSeen(context: Context) {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(seenKey, true)
            .apply()
    }
}
