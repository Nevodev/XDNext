package com.nevoit.xdnext.data.timetable

import kotlinx.datetime.LocalTime

/**
 * One thing a grid block is holding.
 *
 * A block can hold several of these at once, because the grid merges arrangements that overlap in time
 * — two courses that clash, or a course and an exam in the same slot — into a single rectangle with a
 * marker and a count.
 *
 * The sealed interface is the seam that matters most for what comes next: an exam, a physics
 * experiment and a locally added course are all things the original drew in this same grid and
 * described in this same detail sheet. Each becomes a variant here, and every `when` over it — the
 * sheet, the calendar export — fails to compile until it is handled, which is cheaper than noticing
 * on a device that an exam's sheet is blank.
 */
sealed interface ClassBlockEntry {

    /** A registrar course, with the arrangement that placed it. */
    data class Course(
        val detail: ClassDetail,
        val arrangement: TimeArrangement,
    ) : ClassBlockEntry
}

/**
 * One rectangle in the week grid.
 *
 * [start] and [stop] are **block indices**, not periods and not clock times: 0 is the top of the
 * first period and [TimetableLayout.TotalBlocks] the bottom of the last one, with the breaks taking
 * their three blocks each in between. That is what lets a class, an exam and a two-hour experiment
 * share one coordinate system — a period-based one could not express an exam that starts at 09:00.
 *
 * [paletteIndex] is the **source's own** index for the entry — the course's detail index, and later the
 * exam's position in the exam list — and the palette wraps it. The original did the wrapping where the
 * colour was chosen (`colorList[i.index % colorList.length]`); keeping the raw index here means this
 * layer never has to know how many colours there are.
 */
data class ClassBlock(
    val entries: List<ClassBlockEntry>,
    val start: Double,
    val stop: Double,
    val name: String,
    val place: String?,
    val paletteIndex: Int,
) {

    /**
     * Whether this rectangle is several arrangements at once.
     *
     * The card draws a corner triangle and a "还有 N 个日程" line for these — the original's
     * `data.length > 1`.
     */
    val isMerged: Boolean get() = entries.size > 1

    /** How many arrangements this rectangle holds. */
    val entryCount: Int get() = entries.size

    /** How tall the rectangle is, in blocks. */
    val blocks: Double get() = stop - start

    companion object {

        /**
         * A registrar course, placed by its periods rather than by clock times.
         *
         * The two conversions are not inverses — see [TimetableLayout] — which is why a class from
         * period 4 to period 5 correctly spans the noon break while one from period 4 to period 4 does
         * not.
         */
        fun ofCourse(detail: ClassDetail, arrangement: TimeArrangement): ClassBlock = ClassBlock(
            entries = listOf(ClassBlockEntry.Course(detail, arrangement)),
            start = TimetableLayout.startBlockOf(arrangement.start),
            stop = TimetableLayout.stopBlockOf(arrangement.stop),
            name = detail.name,
            place = arrangement.classroom,
            paletteIndex = arrangement.index,
        )

        /**
         * A block placed by a pair of clock times, for a source that has them.
         *
         * Exams and experiments are the original's two: they are announced as "09:00 to 11:00" and have
         * no period numbers at all, so they are projected through [TimetableLayout.blockOf] instead.
         * [entry] is what the detail sheet will describe; it is a [ClassBlockEntry.Course] today only
         * because no other variant exists yet.
         */
        fun ofTimed(
            entry: ClassBlockEntry,
            name: String,
            place: String?,
            paletteIndex: Int,
            startTime: LocalTime,
            stopTime: LocalTime,
        ): ClassBlock = ClassBlock(
            entries = listOf(entry),
            start = TimetableLayout.blockOf(startTime.hour, startTime.minute),
            stop = TimetableLayout.blockOf(stopTime.hour, stopTime.minute),
            name = name,
            place = place,
            paletteIndex = paletteIndex,
        )
    }
}
