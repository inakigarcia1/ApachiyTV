package com.nuvio.tv.ui.screens.player.autosync

import kotlin.math.max

/**
 * Groups near-identical embedded tracks so copies of one source cannot end a candidate search
 * on their own. Outlier rejection of a whole reference track is intentionally not used here.
 */
internal object AutoSyncReferenceConsistency {
    private const val SAME_SOURCE_MIN_OVERLAP = 0.90

    /** Near-identical timing (e.g. CHS / CHT / bilingual variants of one source). */
    internal fun isSameSource(
        first: AutoSyncTimelineRetimer.PreparedActivity,
        second: AutoSyncTimelineRetimer.PreparedActivity,
    ): Boolean = fineActivityOverlap(first, second) >= SAME_SOURCE_MIN_OVERLAP

    /** Jaccard overlap of the 100 ms activity bins at zero offset. */
    private fun fineActivityOverlap(
        first: AutoSyncTimelineRetimer.PreparedActivity,
        second: AutoSyncTimelineRetimer.PreparedActivity,
    ): Double {
        val a = first.fine.packed
        val b = second.fine.packed
        var intersection = 0
        var union = 0
        for (word in 0 until max(a.size, b.size)) {
            val left = a.getOrElse(word) { 0L }
            val right = b.getOrElse(word) { 0L }
            intersection += (left and right).countOneBits()
            union += (left or right).countOneBits()
        }
        return if (union == 0) 0.0 else intersection.toDouble() / union.toDouble()
    }
}
