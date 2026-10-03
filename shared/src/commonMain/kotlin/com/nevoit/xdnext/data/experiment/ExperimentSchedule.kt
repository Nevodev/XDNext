package com.nevoit.xdnext.data.experiment

import com.nevoit.xdnext.data.timetable.ClassBlock
import com.nevoit.xdnext.data.timetable.ClassBlockEntry
import com.nevoit.xdnext.data.timetable.DayBlock
import kotlinx.datetime.LocalDate

/**
 * The blocks one week's grid gets from the experiment list.
 *
 * This is the physics experiment module's half of the timetable's [com.nevoit.xdnext.data.timetable.TimetableOverlay]
 * seam: the grid asks each cell which rectangles are in it, and this answers for a week at a time. The
 * original did the same arithmetic inline inside its `getArrangement`
 *
 * ```dart
 * int diff = timeRange.$1.difference(startDay).inDays;
 * if (diff ~/ 7 == weekIndex && diff % 7 + 1 == dayIndex) { … }
 * ```
 *
 * — a week index computed from the term's first day. Here the week **is** the first day it is asked
 * about ([weekStart]), so the same question is a subtraction: a sitting belongs to the week it falls in,
 * and its column is that difference of dates plus one. That removes the term start day from this
 * calculation entirely, which is what lets the experiments be placed before the timetable has decided
 * anything about its own weeks.
 *
 * What the two implementations agree on, and what matters:
 *
 *  - a sitting before the requested week contributes nothing, exactly as the original's
 *    `isBefore(startDay)` guard dropped them;
 *  - a booking whose sittings span several weeks puts a block in each of them;
 *  - the rectangle's position is [ClassBlock.ofTimed], the same clock-time-to-block projection the
 *    current-time line uses, because a sitting is announced as "15:55 to 18:10" and has no period
 *    numbers at all.
 */
fun experimentDayBlocks(entries: List<ExperimentEntry>, weekStart: LocalDate): List<DayBlock> {
    if (entries.isEmpty()) return emptyList()

    val blocks = mutableListOf<DayBlock>()
    entries.forEachIndexed { index, entry ->
        entry.timeRanges.forEach { range ->
            val offset = (range.start.date.toEpochDays() - weekStart.toEpochDays()).toInt()
            if (offset !in 0..6) return@forEach

            blocks += DayBlock(
                day = offset + 1,
                block = ClassBlock.ofTimed(
                    entry = ClassBlockEntry.Experiment(entry),
                    name = entry.name,
                    place = entry.classroom,
                    // The booking's own position in the list, which is what its colour wraps around —
                    // the original's `colorList[experiments.indexOf(experiment) % colorList.length]`.
                    paletteIndex = index,
                    startTime = range.start.time,
                    stopTime = range.stop.time,
                ),
            )
        }
    }
    return blocks
}
