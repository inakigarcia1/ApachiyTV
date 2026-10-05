package com.nuvio.tv.ui.screens.player.autosync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object AutoSyncSyncedSubtitle {
    private val _url = MutableStateFlow<String?>(null)
    val url: StateFlow<String?> = _url.asStateFlow()

    fun mark(subtitleUrl: String) {
        _url.value = subtitleUrl
    }

    fun clear() {
        _url.value = null
    }
}
