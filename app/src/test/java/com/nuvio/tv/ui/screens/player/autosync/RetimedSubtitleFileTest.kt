package com.nuvio.tv.ui.screens.player.autosync

import com.nuvio.tv.ui.screens.player.SubtitleSyncCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetimedSubtitleFileTest {
    @Test
    fun uniformOffsetIsWrittenIntoTheFile() {
        val original = listOf(
            SubtitleSyncCue(0L, 1_000L, "Hola"),
            SubtitleSyncCue(2_000L, 3_000L, "Chau"),
        )
        val timeline = timeline(
            cues = listOf(
                AutoSyncRetimedCue(0L, 1_000L, 400L, 1_400L),
                AutoSyncRetimedCue(2_000L, 3_000L, 2_400L, 3_400L),
            ),
            scale = 1.0,
            interceptMs = 400.0,
        )

        val body = renderRetimedSrt(original, timeline)

        assertTrue(body.contains("00:00:00,400 --> 00:00:01,400"))
        assertTrue(body.contains("00:00:02,400 --> 00:00:03,400"))
        assertTrue(body.contains("Hola"))
    }

    @Test
    fun scaleCannotBeExpressedAsOnePlayerDelay() {
        val original = listOf(
            SubtitleSyncCue(1_000L, 2_000L, "Uno"),
            SubtitleSyncCue(10_000L, 11_000L, "Dos"),
        )
        val timeline = timeline(
            cues = emptyList(),
            scale = 1.04,
            interceptMs = 100.0,
        )

        val bounds = retimedBounds(original, timeline)

        assertEquals(1_140L, bounds[0].first)
        assertEquals(2_180L, bounds[0].second)
        assertEquals(10_500L, bounds[1].first)
        assertEquals(11_540L, bounds[1].second)
        assertTrue(bounds[0].first - original[0].startTimeMs != bounds[1].first - original[1].startTimeMs)
    }

    private fun timeline(
        cues: List<AutoSyncRetimedCue>,
        scale: Double,
        interceptMs: Double,
    ) = AutoSyncTimelineRetimeResult(
        cues = cues,
        groups = emptyList(),
        targetCoverage = 1.0,
        referenceCoverage = 1.0,
        skippedTargetCues = 0,
        skippedReferenceCues = 0,
        longestTargetSkipRun = 0,
        averageGroupCost = 0.0,
        oneToOneGroups = cues.size,
        oneToTwoGroups = 0,
        twoToOneGroups = 0,
        oneToThreeGroups = 0,
        threeToOneGroups = 0,
        twoToTwoGroups = 0,
        confident = true,
        alignmentScale = scale,
        alignmentInterceptMs = interceptMs,
    )
}
