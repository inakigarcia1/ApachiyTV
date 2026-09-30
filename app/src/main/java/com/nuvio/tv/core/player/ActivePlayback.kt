package com.nuvio.tv.core.player

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Stops in-process playback when the activity stays up but the player
 * screen leaves composition (logout, session loss).
 */
object ActivePlayback {
    private val stoppers = CopyOnWriteArrayList<() -> Unit>()

    fun register(stop: () -> Unit): () -> Unit {
        stoppers.add(stop)
        return { stoppers.remove(stop) }
    }

    fun stop() {
        stoppers.toList().forEach { stopper ->
            runCatching { stopper() }
        }
    }
}
