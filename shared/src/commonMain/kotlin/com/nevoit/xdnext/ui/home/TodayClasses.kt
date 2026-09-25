package com.nevoit.xdnext.ui.home

import com.nevoit.xdnext.data.timetable.ClassBlock
import com.nevoit.xdnext.data.timetable.ClassBlockEntry
import com.nevoit.xdnext.data.timetable.TimetableCalendar
import com.nevoit.xdnext.data.timetable.TimetableGrid
import com.nevoit.xdnext.data.timetable.TimetableLayout
import com.nevoit.xdnext.data.timetable.TimetableState
import kotlinx.datetime.LocalDateTime

/**
 * One class the campus page's banner still has to show today.
 *
 * Four strings and a colour index rather than a `ClassBlock`: none of the grid's geometry survives the
 * trip, which is what keeps the page's renderer free of the timetable's own types — and what lets the
 * decision below be tested without a composition.
 */
internal data class TodayClass(
    val name: String,
    val place: String?,
    val startText: String,
    val stopText: String,
    /** The course's own index into the shared palette, so the colour here matches the grid's. */
    val paletteIndex: Int,
)

/**
 * The classes left today, in the order they start.
 *
 * "Left" includes the class **in progress**: a lecture from 08:30 to 10:05 is still a remaining class at
 * 09:00 and stops being one when it ends rather than when it starts. That is the rule the grid's own
 * current-time indicator draws by, and the two must agree — a banner that counted a class as gone while
 * the grid drew a line through it would be a disagreement the user can see.
 *
 * Empty whenever the question cannot be answered: no timetable, no term start day, a date before the
 * term, or a day with nothing on it. All four draw as an empty list, and the count on the left of the
 * banner is what tells them apart — see `FocusScreen`.
 */
internal fun remainingClassesToday(state: TimetableState, now: LocalDateTime): List<TodayClass> {
    val data = state.data ?: return emptyList()
    val termStart = state.termStartDay ?: return emptyList()

    val weekIndex = TimetableCalendar.currentWeek(termStart, now.date)
    // `-1` is the calendar's "the term has not started", and there is no week to read in that case.
    if (weekIndex < 0) return emptyList()

    val blocks = TimetableGrid(data).blocks(
        weekIndex = weekIndex,
        // The registrar's own day numbering, Monday first — the same expression the date row uses.
        dayIndex = now.date.dayOfWeek.ordinal + 1,
        weekStart = TimetableCalendar.weekStart(termStart, weekIndex),
    )
    val nowBlock = TimetableLayout.blockOfMinuteOfDay(now.hour * 60 + now.minute)

    return blocks.mapNotNull { block -> block.asTodayClass(nowBlock) }
}

/**
 * The banner's view of one block, or null when this card has nothing to say about it.
 *
 * A block with no course entry was contributed by an *overlay* — an exam, an experiment, a locally added
 * course. Nothing hands the campus page an overlay yet, so this cannot happen today; answering "nothing"
 * rather than inventing times out of the geometry is what keeps the two layers from disagreeing when the
 * first overlay arrives.
 */
private fun ClassBlock.asTodayClass(nowBlock: Double): TodayClass? {
    if (stop <= nowBlock) return null

    val courses = entries.filterIsInstance<ClassBlockEntry.Course>()
    if (courses.isEmpty()) return null

    // A merged block holds several arrangements, and the block's own rectangle is their union — so its
    // span is the earliest start to the latest end, which is what the two lines have to show.
    val startMinute = courses.minOf { TimetableLayout.periodStartMinute(it.arrangement.start) }
    val stopMinute = courses.maxOf { TimetableLayout.periodStopMinute(it.arrangement.stop) }

    return TodayClass(
        name = name,
        place = place,
        startText = clockText(startMinute),
        stopText = clockText(stopMinute),
        paletteIndex = paletteIndex,
    )
}

/** `HH:mm`, the same shape the grid's own current-time label is drawn in. */
private fun clockText(minuteOfDay: Int): String {
    val hour = (minuteOfDay / 60).coerceIn(0, 23)
    val minute = (minuteOfDay % 60).coerceIn(0, 59)
    return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}
