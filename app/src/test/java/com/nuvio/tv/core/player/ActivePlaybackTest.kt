package com.nuvio.tv.core.player

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivePlaybackTest {

    @Test
    fun stop_runs_registered_callback_until_unregistered() {
        var calls = 0
        val unregister = ActivePlayback.register { calls++ }
        ActivePlayback.stop()
        unregister()
        ActivePlayback.stop()
        assertEquals(1, calls)
    }
}
