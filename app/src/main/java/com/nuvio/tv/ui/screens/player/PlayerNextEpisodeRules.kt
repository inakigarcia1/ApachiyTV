package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.data.local.NextEpisodeThresholdMode
import kotlin.math.roundToInt
import com.nuvio.tv.data.repository.SkipInterval
import com.nuvio.tv.core.util.isEpisodeReleaseAired
import com.nuvio.tv.core.util.parseEpisodeReleaseLocalDate
import com.nuvio.tv.domain.model.Video
import java.time.Clock
import java.time.LocalDate

object PlayerNextEpisodeRules {
    fun resolveNextEpisode(
        videos: List<Video>,
        currentSeason: Int?,
        currentEpisode: Int
    ): Video? {
        // Absolute-numbered content (e.g. some anime via Kitsu) carries no season; order by episode
        // number alone and advance to the next one.
        if (currentSeason == null) {
            val sorted = videos
                .filter { it.episode != null }
                .sortedWith(compareBy<Video>({ it.season ?: 0 }, { it.episode ?: 0 }))
            val index = sorted.indexOfFirst { it.episode == currentEpisode }
            return if (index < 0) null else sorted.getOrNull(index + 1)
        }

        val sortedEpisodes = videos
            .filter { it.season != null && it.episode != null }
            .sortedWith(compareBy<Video> { it.season ?: Int.MAX_VALUE }.thenBy { it.episode ?: Int.MAX_VALUE })

        val currentIndex = sortedEpisodes.indexOfFirst {
            it.season == currentSeason && it.episode == currentEpisode
        }
        if (currentIndex < 0) return null

        return sortedEpisodes.getOrNull(currentIndex + 1)
    }

    fun shouldShowNextEpisodeCard(
        positionMs: Long,
        durationMs: Long,
        skipIntervals: List<SkipInterval>,
        thresholdMode: NextEpisodeThresholdMode,
        thresholdPercent: Float,
        thresholdMinutesBeforeEnd: Float,
        useAdaptiveLead: Boolean = false,
        anime: Boolean = false,
    ): Boolean {
        val promptAtMs = nextEpisodePromptPositionMs(
            durationMs = durationMs,
            skipIntervals = skipIntervals,
            thresholdMode = thresholdMode,
            thresholdPercent = thresholdPercent,
            thresholdMinutesBeforeEnd = thresholdMinutesBeforeEnd,
            useAdaptiveLead = useAdaptiveLead,
            anime = anime,
        ) ?: return false
        return positionMs >= promptAtMs
    }

    fun shouldPreloadNextEpisodeSources(
        positionMs: Long,
        durationMs: Long,
        skipIntervals: List<SkipInterval>,
        thresholdMode: NextEpisodeThresholdMode,
        thresholdPercent: Float,
        thresholdMinutesBeforeEnd: Float,
        useAdaptiveLead: Boolean = false,
        anime: Boolean = false,
    ): Boolean {
        val promptAtMs = nextEpisodePromptPositionMs(
            durationMs = durationMs,
            skipIntervals = skipIntervals,
            thresholdMode = thresholdMode,
            thresholdPercent = thresholdPercent,
            thresholdMinutesBeforeEnd = thresholdMinutesBeforeEnd,
            useAdaptiveLead = useAdaptiveLead,
            anime = anime,
        ) ?: return false
        return positionMs >= (promptAtMs - PRELOAD_LEAD_MS).coerceAtLeast(0L)
    }

    /**
     * Playback position where the next-episode card appears.
     * Credits that finish near the file end open the card at the credits start.
     * Otherwise the card uses the duration table, unless a saved percentage or
     * minutes-before-end setting replaces it.
     */
    fun nextEpisodePromptPositionMs(
        durationMs: Long,
        skipIntervals: List<SkipInterval>,
        thresholdMode: NextEpisodeThresholdMode,
        thresholdPercent: Float,
        thresholdMinutesBeforeEnd: Float,
        useAdaptiveLead: Boolean = false,
        anime: Boolean = false,
    ): Long? {
        if (durationMs <= 0L) return null
        val userThresholdMs = thresholdWindowFromEndMs(
            durationMs = durationMs,
            thresholdMode = thresholdMode,
            thresholdPercent = thresholdPercent,
            thresholdMinutesBeforeEnd = thresholdMinutesBeforeEnd,
            useAdaptiveLead = useAdaptiveLead,
            anime = anime,
        )
        val outroSegments = skipIntervals.filter { it.type in OUTRO_SEGMENT_TYPES }
        if (outroSegments.isNotEmpty()) {
            val latestOutroEndMs = (outroSegments.maxOf { it.endTime } * 1_000.0).toLong()
            val postOutroGapMs = durationMs - latestOutroEndMs
            if (postOutroGapMs <= userThresholdMs) {
                return (outroSegments.minOf { it.startTime } * 1_000.0).toLong()
            }
        }
        return (durationMs - userThresholdMs).coerceAtLeast(0L)
    }

    private fun thresholdWindowFromEndMs(
        durationMs: Long,
        thresholdMode: NextEpisodeThresholdMode,
        thresholdPercent: Float,
        thresholdMinutesBeforeEnd: Float,
        useAdaptiveLead: Boolean,
        anime: Boolean,
    ): Long {
        if (useAdaptiveLead) return adaptiveLeadMs(durationMs, anime)
        return when (thresholdMode) {
            NextEpisodeThresholdMode.PERCENTAGE -> {
                val steps = (thresholdPercent.coerceIn(THRESHOLD_PERCENT_MIN, THRESHOLD_PERCENT_MAX) * 2f).roundToInt()
                durationMs * (200 - steps) / 200
            }
            NextEpisodeThresholdMode.MINUTES_BEFORE_END -> {
                val clampedMinutes = thresholdMinutesBeforeEnd.coerceIn(0f, 3.5f)
                (clampedMinutes * 60_000f).toLong()
            }
        }
    }

    fun adaptiveLeadMs(durationMs: Long, anime: Boolean): Long {
        if (durationMs <= 0L) return 0L
        val minutes = durationMs / 60_000.0
        val row = when {
            minutes >= 120.0 -> ADAPTIVE_LEAD_ROWS.last()
            minutes <= 20.0 -> ADAPTIVE_LEAD_ROWS.first()
            else -> ADAPTIVE_LEAD_ROWS.minWith(
                compareBy<Pair<Int, Pair<Int, Int>>> { kotlin.math.abs(it.first - minutes) }
                    .thenBy { it.first },
            )
        }
        val seconds = if (anime) row.second.second else row.second.first
        return seconds * 1_000L
    }

    fun isAnimePlayback(
        contentType: String?,
        ids: List<String?>,
        genres: List<String>,
        country: String?,
    ): Boolean {
        if (contentType.equals("anime", ignoreCase = true)) return true
        if (ids.any { id ->
                val value = id?.trim().orEmpty()
                value.startsWith("kitsu:", ignoreCase = true) ||
                    value.startsWith("mal:", ignoreCase = true) ||
                    value.startsWith("anilist:", ignoreCase = true)
            }
        ) return true
        if (genres.any { it.equals("anime", ignoreCase = true) }) return true
        return genres.any { it.equals("animation", ignoreCase = true) } &&
            country?.contains("Japan", ignoreCase = true) == true
    }

    private val ADAPTIVE_LEAD_ROWS = listOf(
        20 to (12 to 75),
        23 to (12 to 75),
        25 to (15 to 75),
        30 to (18 to 75),
        35 to (25 to 75),
        40 to (30 to 75),
        45 to (45 to 90),
        50 to (60 to 90),
        55 to (75 to 90),
        60 to (105 to 105),
        90 to (120 to 120),
        105 to (135 to 135),
        120 to (150 to 150),
    )

    fun parseEpisodeReleaseDate(raw: String?): LocalDate? {
        return parseEpisodeReleaseLocalDate(raw)
    }

    fun hasEpisodeAired(raw: String?, clock: Clock = Clock.systemDefaultZone()): Boolean {
        return isEpisodeReleaseAired(raw, clock) ?: true
    }

    val OUTRO_SEGMENT_TYPES = setOf("outro", "ed", "mixed-ed")

    const val THRESHOLD_PERCENT_MIN = 85f
    const val THRESHOLD_PERCENT_MAX = 100f
    const val THRESHOLD_PERCENT_DEFAULT = 90f
    const val PRELOAD_LEAD_MS = 15_000L

    const val POST_OUTRO_AUTOPLAY_GAP_MS = 5_000L

    const val END_OF_VIDEO_EPSILON_MS = 1_000L
}
