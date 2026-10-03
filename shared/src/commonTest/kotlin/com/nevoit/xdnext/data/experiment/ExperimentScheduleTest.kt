package com.nevoit.xdnext.data.experiment

import com.nevoit.xdnext.data.timetable.ClassBlockEntry
import com.nevoit.xdnext.data.timetable.TimetableLayout
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for where an experiment lands on the grid.
 *
 * Three things can be wrong here and all three are visible only as a rectangle in the wrong place: which
 * *week* a sitting belongs to, which *column*, and how far down. The week and the column come from the
 * difference between the sitting's date and the week's first day — the original's `diff ~/ 7` and
 * `diff % 7 + 1` collapsed into one comparison, because the caller already knows which week it is asking
 * about — so what is pinned is that arithmetic at the week's two edges.
 *
 * The vertical position is not tested by number: it is [com.nevoit.xdnext.data.timetable.ClassBlock.ofTimed],
 * the same clock-time-to-block projection the current-time line uses, and what the test checks is that the
 * *times* are handed to it — a period number passed there would land the rectangle in the wrong row and
 * nothing else would notice.
 */
class ExperimentScheduleTest {

    private val weekStart = LocalDate(2026, 3, 2) // a Monday

    @Test
    fun placesASittingOnItsOwnDayOfTheRequestedWeek() {
        // 2026-03-05 is the Thursday of the week beginning 2026-03-02.
        val blocks = experimentDayBlocks(listOf(entry(on = LocalDate(2026, 3, 5))), weekStart)

        assertEquals(1, blocks.size)
        assertEquals(4, blocks.single().day, "Monday is 1, so Thursday is 4.")
    }

    @Test
    fun aSittingOutsideTheWeekContributesNothing() {
        val entries = listOf(
            entry(on = LocalDate(2026, 3, 1)), // the Sunday before
            entry(on = LocalDate(2026, 3, 9)), // the Monday after
        )

        assertTrue(experimentDayBlocks(entries, weekStart).isEmpty())
    }

    @Test
    fun theWeekIsWhateverItIsAskedAbout() {
        val entries = listOf(entry(on = LocalDate(2026, 3, 9)))

        val blocks = experimentDayBlocks(entries, LocalDate(2026, 3, 9))

        assertEquals(1, blocks.single().day, "The same sitting is the next week's Monday.")
    }

    @Test
    fun theVerticalPositionComesFromTheClockTimes() {
        val blocks = experimentDayBlocks(listOf(entry(on = LocalDate(2026, 3, 5))), weekStart)

        val block = blocks.single().block
        assertEquals(TimetableLayout.blockOf(15, 55), block.start)
        assertEquals(TimetableLayout.blockOf(18, 10), block.stop)
        assertTrue(block.stop > block.start)
    }

    @Test
    fun oneBookingWithSeveralSittingsGetsABlockEach() {
        val spread = entry(
            ranges = listOf(
                range(LocalDate(2026, 3, 5)),
                range(LocalDate(2026, 3, 12)),
            ),
        )

        val thisWeek = experimentDayBlocks(listOf(spread), weekStart)
        val nextWeek = experimentDayBlocks(listOf(spread), LocalDate(2026, 3, 9))

        assertEquals(1, thisWeek.size, "Only the sitting inside the asked-for week is drawn.")
        assertEquals(1, nextWeek.size)
        assertEquals(LocalDate(2026, 3, 5), spread.timeRanges.first().start.date)
        assertEquals(4, thisWeek.single().day)
        assertEquals(4, nextWeek.single().day, "The same weekday, one week later.")
    }

    @Test
    fun aBlockCarriesTheBookingAndItsOwnColourIndex() {
        val first = entry(on = LocalDate(2026, 3, 5), name = "示波器的使用")
        val second = entry(on = LocalDate(2026, 3, 6), name = "霍尔效应")

        val blocks = experimentDayBlocks(listOf(first, second), weekStart)

        assertEquals(listOf(0, 1), blocks.map { it.block.paletteIndex })
        assertEquals(listOf("示波器的使用", "霍尔效应"), blocks.map { it.block.name })
        assertEquals("实验楼A201", blocks.first().block.place)
        val variant = assertIs<ClassBlockEntry.Experiment>(blocks.first().block.entries.single())
        assertEquals("示波器的使用", variant.entry.name)
    }

    // --- Fixtures ---------------------------------------------------------------------------------

    private fun entry(
        on: LocalDate = LocalDate(2026, 3, 5),
        name: String = "示波器的使用",
        ranges: List<ExperimentTimeRange> = listOf(range(on)),
    ) = ExperimentEntry(
        name = name,
        classroom = "实验楼A201",
        timeRanges = ranges,
        teacher = "张三",
    )

    private fun range(date: LocalDate) = ExperimentTimeRange(
        start = LocalDateTime(date, kotlinx.datetime.LocalTime(15, 55)),
        stop = LocalDateTime(date, kotlinx.datetime.LocalTime(18, 10)),
    )
}
