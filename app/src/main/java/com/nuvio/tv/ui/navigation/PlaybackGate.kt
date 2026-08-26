package com.nuvio.tv.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf

val LocalPlaybackGate = staticCompositionLocalOf<(navigate: () -> Unit) -> Unit> { { it() } }
