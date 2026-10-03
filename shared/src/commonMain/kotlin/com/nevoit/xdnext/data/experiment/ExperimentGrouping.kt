package com.nevoit.xdnext.data.experiment

import kotlinx.datetime.LocalDateTime

/**
 * One experiment list, split by when its sittings are relative to the clock.
 *
 * The original's page drew three sections — 正在进行实验, 未完成实验 and 已完成实验 — and this is that
 * split as a value, so the page decides only how to draw it. A booking appears in more than one group
 * when its sittings straddle the clock: a week-long experiment whose Monday sitting is over and whose
 * Thursday one has not started is both 未完成 and 已完成, because the two sentences are about sittings
 * rather than about bookings.
 *
 * Every list is ordered by its **earliest** sitting, which is the original's own ordering
 * (`_sortExperiments`), and each booking keeps only the sittings that put it in that group.
 */
data class ExperimentGroups(
    /** Sittings that are happening now. */
    val ongoing: List<ExperimentEntry>,
    /** Sittings that have not started. */
    val upcoming: List<ExperimentEntry>,
    /** Sittings that are over. */
    val finished: List<ExperimentEntry>,
) {

    /** Whether there is nothing at all to draw. */
    val isEmpty: Boolean get() = ongoing.isEmpty() && upcoming.isEmpty() && finished.isEmpty()
}

/**
 * Splits [entries] into the three groups, as of [now].
 *
 * The comparison is against the clock the caller passes. The original compared against **now plus one
 * day**, which filed a sitting that finishes this evening as 已完成 from midnight onwards — a booking
 * the user is about to walk into, drawn under a heading that says it is over. The off-by-a-day is not
 * reproduced: with the real clock the three headings mean what they say.
 *
 * A bound is inclusive at the start and exclusive at the end — a sitting is 正在进行 from its start
 * minute, and 已完成 from the moment it is over — which keeps a sitting out of both 正在进行 and
 * 已完成 at exactly its stop time.
 */
fun groupExperiments(entries: List<ExperimentEntry>, now: LocalDateTime): ExperimentGroups {
    val ongoing = mutableListOf<ExperimentEntry>()
    val upcoming = mutableListOf<ExperimentEntry>()
    val finished = mutableListOf<ExperimentEntry>()

    entries.forEach { entry ->
        val started = entry.timeRanges.filter { it.start <= now }
        val notStarted = entry.timeRanges.filter { it.start > now }

        ongoing += entry.withRanges(started.filter { now < it.stop })
        finished += entry.withRanges(started.filter { it.stop <= now })
        upcoming += entry.withRanges(notStarted)
    }

    return ExperimentGroups(
        ongoing = ongoing.sortedByEarliestSitting(),
        upcoming = upcoming.sortedByEarliestSitting(),
        finished = finished.sortedByEarliestSitting(),
    )
}

/** The entry with only [ranges], or nothing at all when the group has no sitting of it. */
private fun ExperimentEntry.withRanges(ranges: List<ExperimentTimeRange>): List<ExperimentEntry> =
    if (ranges.isEmpty()) emptyList() else listOf(copy(timeRanges = ranges))

/** Ordered by the booking's first sitting, as the original ordered every one of its three lists. */
private fun List<ExperimentEntry>.sortedByEarliestSitting(): List<ExperimentEntry> =
    sortedBy { entry -> entry.timeRanges.minOf { it.start } }
