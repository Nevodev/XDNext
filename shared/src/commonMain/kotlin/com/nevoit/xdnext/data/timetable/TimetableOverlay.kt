package com.nevoit.xdnext.data.timetable

import kotlinx.datetime.LocalDate

/**
 * An extra source of grid blocks, drawn on the same week as the registrar's timetable.
 *
 * This is the seam the rest of the academic modules arrive through. The original projected four
 * sources onto one grid — courses, exams, physics experiments and other experiments — plus locally
 * added courses, and did it by hard-coding all five loops inside one method
 * (`ClassTableWidgetState.getArrangement`). Each source here answers for one week and hands back
 * blocks the grid can place without knowing what they are:
 *
 * ```kotlin
 * val exams = TimetableOverlay { weekStart -> examRepository.blocksIn(weekStart) }
 * ```
 *
 * What the grid then does with them is the *same* thing it does with a course: sort by start, merge
 * anything that overlaps, and draw one rectangle. Adding a source therefore cannot change how courses
 * are laid out, which is the property that made five hard-coded loops in one method worth replacing.
 *
 * The week is handed over as its first day rather than as an index because the sources that are
 * coming are all *dated*: an exam knows it is on the 14th, and which week that is depends on the term
 * start day, which the timetable owns. The original made the same conversion inline —
 * `diff = startTime.difference(startDay).inDays; diff ~/ 7 == weekIndex` — at every one of its four
 * sites.
 */
fun interface TimetableOverlay {

    /**
     * Every block this source contributes to the week beginning [weekStart].
     *
     * Called once per week that is drawn, so it must be cheap; an overlay that has to fetch should
     * hold what it fetched rather than fetching here.
     */
    fun blocksFor(weekStart: LocalDate): List<DayBlock>
}

/**
 * A block and the day of the week it belongs on.
 *
 * [day] is 1..7 with Monday first, matching [TimeArrangement.day] and the column the grid draws it in.
 */
data class DayBlock(val day: Int, val block: ClassBlock)
