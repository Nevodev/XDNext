package com.nevoit.xdnext.data.timetable

import kotlinx.datetime.LocalDate
import kotlin.math.max
import kotlin.math.min

/**
 * One week of the timetable, arranged: which rectangles are in which cell of the grid.
 *
 * Ported from `ClassTableWidgetState.getArrangement` and the overlap merge that followed it in the
 * same method. Two steps, and they are separate on purpose:
 *
 * 1. **Gather.** Every arrangement of this course list whose week flag is set for this week and whose
 *    day is this day; plus whatever the [overlays] contribute for the same week and day. Overlays are
 *    how exams, experiments and locally added courses will arrive — the original hard-coded all of
 *    them here, this takes them as a list.
 * 2. **Merge.** Sort by start, then fold each rectangle into the first one it overlaps. Overlap is
 *    normal rather than exceptional: a course can clash with another, and a make-up session can land
 *    on top of a regular one. The merged rectangle spans both, keeps the *larger* one's name and
 *    colour, and remembers both entries so the card can say how many there are.
 *
 * The grid is asked for one cell at a time because that is how the page draws it — a stack of
 * positioned rectangles per day column — and because the week's other cells are not needed to place
 * this one. That last part is what stops this from being a whole-week layout pass that re-runs on
 * every frame.
 *
 * Immutable and cheap to construct: the page holds one per recomposition and asks it for cells, rather
 * than rebuilding it per cell.
 */
class TimetableGrid(
    private val data: ClassTableData,
    private val overlays: List<TimetableOverlay> = emptyList(),
) {

    /**
     * The rectangles in the cell at [weekIndex] (zero-based) and [dayIndex] (1..7, Monday first).
     *
     * [weekStart] is that week's first day, which is what an overlay needs in order to decide whether
     * its own dated events fall in this week. It is passed rather than derived here so that this class
     * does not have to know how a term start day becomes a week — [TimetableCalendar] does, and it may
     * answer `null` for a term whose start day is missing.
     */
    fun blocks(weekIndex: Int, dayIndex: Int, weekStart: LocalDate): List<ClassBlock> {
        val events = mutableListOf<ClassBlock>()

        data.timeArrangement.forEachIndexed { position, arrangement ->
            if (arrangement.day != dayIndex) return@forEachIndexed
            if (weekIndex >= arrangement.weekList.size) return@forEachIndexed
            if (!arrangement.weekList[weekIndex]) return@forEachIndexed

            // The detail is resolved by position rather than by `indexOf`, because two arrangements of
            // different courses can be structurally equal and `indexOf` would then answer with the
            // first of them. The original resolved it through `indexOf` on the list it was iterating.
            val detail = data.detailForArrangementAt(position)
            events.add(ClassBlock.ofCourse(detail, arrangement))
        }

        for (overlay in overlays) {
            for (dayBlock in overlay.blocksFor(weekStart)) {
                if (dayBlock.day == dayIndex) events.add(dayBlock.block)
            }
        }

        events.sortBy { it.start }
        return merge(events)
    }

    /**
     * Folds overlapping rectangles together.
     *
     * The original's own algorithm, kept as it is rather than replaced by a sweep: it looks only at the
     * *first* rectangle already placed that this one overlaps, and when it merges, it does not
     * re-examine the result against the others. A general interval merge would produce different
     * rectangles for three-way overlaps, and the page's look — one rectangle per clash group, named
     * after its largest member — is what a user of the original recognises.
     */
    private fun merge(events: List<ClassBlock>): List<ClassBlock> {
        val arranged = mutableListOf<ClassBlock>()
        for (event in events) {
            val index = arranged.indexOfFirst { overlaps(it, event) }
            if (index < 0) {
                arranged.add(event)
                continue
            }

            val existing = arranged[index]
            // The larger of the two gives the merged rectangle its identity. `>=` rather than `>`, so
            // that equal-length clashes are named after the later one, as in the original.
            val byNewcomer = event.blocks >= existing.blocks
            arranged[index] = ClassBlock(
                entries = existing.entries + event.entries,
                start = min(event.start, existing.start),
                stop = max(event.stop, existing.stop),
                name = if (byNewcomer) event.name else existing.name,
                place = if (byNewcomer) event.place else existing.place,
                paletteIndex = if (byNewcomer) event.paletteIndex else existing.paletteIndex,
            )
        }
        return arranged
    }

    /**
     * Whether two rectangles share any part of the grid.
     *
     * The original's own four-clause test. It is written out rather than expressed as
     * `aStart < bEnd && bStart < aEnd` for one reason that matters: touching rectangles do **not**
     * count. A class ending at block 10 and another starting at block 10 are adjacent, not clashing,
     * and the two expressions disagree exactly there.
     */
    private fun overlaps(a: ClassBlock, b: ClassBlock): Boolean =
        (a.start >= b.start && a.start < b.stop) ||
                (a.stop > b.start && a.stop <= b.stop) ||
                (b.start >= a.start && b.start < a.stop) ||
                (b.stop > a.start && b.stop <= a.stop)
}
