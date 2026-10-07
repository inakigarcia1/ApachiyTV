package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.data.local.NextEpisodeThresholdMode
import com.nuvio.tv.data.repository.SkipInterval
import com.nuvio.tv.domain.model.Video
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerNextEpisodeRulesTest {

    private fun ep(season: Int?, episode: Int?, id: String = "s${season}e${episode}") =
        Video(
            id = id,
            title = id,
            released = null,
            thumbnail = null,
            season = season,
            episode = episode,
            overview = null
        )

    @Test
    fun `advances to next episode in same season`() {
        val videos = listOf(ep(1, 1), ep(1, 2), ep(1, 3))
        val next = PlayerNextEpisodeRules.resolveNextEpisode(videos, currentSeason = 1, currentEpisode = 2)
        assertEquals("s1e3", next?.id)
    }

    @Test
    fun `crosses into next season after the season finale`() {
        val videos = listOf(ep(1, 1), ep(1, 2), ep(2, 1))
        val next = PlayerNextEpisodeRules.resolveNextEpisode(videos, currentSeason = 1, currentEpisode = 2)
        assertEquals("s2e1", next?.id)
    }

    @Test
    fun `returns null after the very last episode`() {
        val videos = listOf(ep(1, 1), ep(1, 2))
        val next = PlayerNextEpisodeRules.resolveNextEpisode(videos, currentSeason = 1, currentEpisode = 2)
        assertNull(next)
    }

    @Test
    fun `returns null when the current episode is not in the list`() {
        val videos = listOf(ep(1, 1), ep(1, 2))
        val next = PlayerNextEpisodeRules.resolveNextEpisode(videos, currentSeason = 3, currentEpisode = 9)
        assertNull(next)
    }

    @Test
    fun `absolute numbering advances by episode when the caller has no season`() {
        // Season-less anime: the caller has no season but the meta videos still carry one.
        val videos = listOf(ep(1, 5), ep(1, 6), ep(1, 7))
        val next = PlayerNextEpisodeRules.resolveNextEpisode(videos, currentSeason = null, currentEpisode = 6)
        assertEquals("s1e7", next?.id)
    }

    @Test
    fun `absolute numbering advances when the meta videos also lack a season`() {
        val videos = listOf(ep(null, 5, "e5"), ep(null, 6, "e6"), ep(null, 7, "e7"))
        val next = PlayerNextEpisodeRules.resolveNextEpisode(videos, currentSeason = null, currentEpisode = 6)
        assertEquals("e7", next?.id)
    }

    @Test
    fun `absolute numbering returns null after the last episode`() {
        val videos = listOf(ep(null, 5, "e5"), ep(null, 6, "e6"))
        val next = PlayerNextEpisodeRules.resolveNextEpisode(videos, currentSeason = null, currentEpisode = 6)
        assertNull(next)
    }

    @Test
    fun `without credits the card opens at 90 percent and sources preload 15 seconds earlier`() {
        val durationMs = 40 * 60_000L
        val promptAt = PlayerNextEpisodeRules.nextEpisodePromptPositionMs(
            durationMs = durationMs,
            skipIntervals = emptyList(),
            thresholdMode = NextEpisodeThresholdMode.PERCENTAGE,
            thresholdPercent = PlayerNextEpisodeRules.THRESHOLD_PERCENT_DEFAULT,
            thresholdMinutesBeforeEnd = 2f,
        )
        assertEquals(36 * 60_000L, promptAt)
        val show = { positionMs: Long ->
            PlayerNextEpisodeRules.shouldShowNextEpisodeCard(
                positionMs = positionMs,
                durationMs = durationMs,
                skipIntervals = emptyList(),
                thresholdMode = NextEpisodeThresholdMode.PERCENTAGE,
                thresholdPercent = 90f,
                thresholdMinutesBeforeEnd = 2f,
            )
        }
        val preload = { positionMs: Long ->
            PlayerNextEpisodeRules.shouldPreloadNextEpisodeSources(
                positionMs = positionMs,
                durationMs = durationMs,
                skipIntervals = emptyList(),
                thresholdMode = NextEpisodeThresholdMode.PERCENTAGE,
                thresholdPercent = 90f,
                thresholdMinutesBeforeEnd = 2f,
            )
        }
        assertFalse(show(promptAt!! - 1))
        assertTrue(show(promptAt))
        assertFalse(preload(promptAt - PlayerNextEpisodeRules.PRELOAD_LEAD_MS - 1))
        assertTrue(preload(promptAt - PlayerNextEpisodeRules.PRELOAD_LEAD_MS))
    }

    @Test
    fun `credits that finish near the end open the card when they start`() {
        val durationMs = 40 * 60_000L
        val credits = listOf(SkipInterval(startTime = 37 * 60.0, endTime = 39 * 60.0 + 50, type = "outro", provider = "introdb"))
        val promptAt = PlayerNextEpisodeRules.nextEpisodePromptPositionMs(
            durationMs = durationMs,
            skipIntervals = credits,
            thresholdMode = NextEpisodeThresholdMode.PERCENTAGE,
            thresholdPercent = 90f,
            thresholdMinutesBeforeEnd = 2f,
        )
        assertEquals(37 * 60_000L, promptAt)
    }

    @Test
    fun `a long scene after the credits waits for the percentage`() {
        val durationMs = 40 * 60_000L
        val credits = listOf(SkipInterval(startTime = 30 * 60.0, endTime = 32 * 60.0, type = "ed", provider = "aniskip"))
        val promptAt = PlayerNextEpisodeRules.nextEpisodePromptPositionMs(
            durationMs = durationMs,
            skipIntervals = credits,
            thresholdMode = NextEpisodeThresholdMode.PERCENTAGE,
            thresholdPercent = 90f,
            thresholdMinutesBeforeEnd = 2f,
        )
        assertEquals(36 * 60_000L, promptAt)
    }

    @Test
    fun `timestamped episode does not air early on its local release day`() {
        val eastern = ZoneId.of("America/Detroit")
        val before = Clock.fixed(Instant.parse("2026-07-15T14:59:59Z"), eastern)
        val exact = Clock.fixed(Instant.parse("2026-07-15T15:00:00Z"), eastern)

        assertFalse(PlayerNextEpisodeRules.hasEpisodeAired("2026-07-15T15:00:00Z", before))
        assertTrue(PlayerNextEpisodeRules.hasEpisodeAired("2026-07-15T15:00:00Z", exact))
    }
}
