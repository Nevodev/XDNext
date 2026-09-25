package com.nevoit.xdnext.data.timetable

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the grid's geometry.
 *
 * This is the part of the timetable that is arithmetic rather than policy, and the arithmetic is the
 * original's — so what is pinned here is precisely the set of numbers that would look plausible if
 * they were wrong. A grid whose breaks are one block out still draws, and every class on it is a few
 * minutes away from where it should be.
 */
class TimetableLayoutTest {

    @Test
    fun everyPeriodSitsWhereTheOriginalPutIt() {
        // start to stop, in blocks: four in the morning, then the three-block 午休, four in the
        // afternoon, the three-block 晚休, then three in the evening.
        val expected = listOf(
            1 to (0.0 to 5.0),
            2 to (5.0 to 10.0),
            3 to (10.0 to 15.0),
            4 to (15.0 to 20.0),
            5 to (23.0 to 28.0),
            6 to (28.0 to 33.0),
            7 to (33.0 to 38.0),
            8 to (38.0 to 43.0),
            9 to (46.0 to 51.0),
            10 to (51.0 to 56.0),
            11 to (56.0 to 61.0),
        )
        for ((period, blocks) in expected) {
            assertEquals(blocks.first, TimetableLayout.startBlockOf(period), "period $period start")
            assertEquals(blocks.second, TimetableLayout.stopBlockOf(period), "period $period stop")
        }
    }

    @Test
    fun theTwoBreaksAreNotInversesOfEachOther() {
        // Period 5 begins three blocks after period 4 ends. That gap *is* the noon break, and it is the
        // reason a period's position is a function and not a multiplication.
        assertEquals(3.0, TimetableLayout.startBlockOf(5) - TimetableLayout.stopBlockOf(4))
        assertEquals(3.0, TimetableLayout.startBlockOf(9) - TimetableLayout.stopBlockOf(8))
    }

    @Test
    fun theIndexColumnAddsUpToTheWholeGrid() {
        val rows = 0 until TimetableLayout.IndexRows
        assertEquals(
            TimetableLayout.TotalBlocks,
            rows.sumOf(TimetableLayout::indexRowBlocks),
            "The thirteen index rows have to fill the grid exactly, or the column ends with a seam.",
        )
    }

    @Test
    fun theIndexColumnNamesEveryPeriodAndBothBreaks() {
        val rows = (0 until TimetableLayout.IndexRows).map(TimetableLayout::indexRow)

        assertEquals(
            (1..11).toList(),
            rows.filterIsInstance<IndexRow.Period>().map { it.number },
        )
        // The breaks are two lines each — the index column is one character wide and the word is two —
        // so the label carries the break itself.
        assertEquals(
            listOf("午\n休", "晚\n休"),
            rows.filterIsInstance<IndexRow.Break>().map { it.label },
        )
    }

    @Test
    fun theDayStartsAtTheTopOfTheGridAndEndsAtTheBottom() {
        assertEquals(0.0, TimetableLayout.blockOf(0, 0))
        assertEquals(0.0, TimetableLayout.blockOf(8, 29))
        assertEquals(0.0, TimetableLayout.blockOf(8, 30), "The first bell is the top of period 1.")
        assertEquals(61.0, TimetableLayout.blockOf(23, 59))
        assertEquals(61.0, TimetableLayout.blockOf(21, 25), "The last bell is the bottom of period 11.")
    }

    @Test
    fun theLineMovesThroughAPeriod() {
        // 08:52 is twenty-two of period 1's forty-five minutes, so the line is a little under halfway
        // down a period that is five blocks tall.
        assertEquals(5.0 * 22.0 / 45.0, TimetableLayout.blockOf(8, 52), absoluteTolerance = 0.001)
    }

    @Test
    fun theLineCrossesTheLunchBreakByTheBreaksOwnHeight() {
        // 13:00 is halfway through the two-hour lunch, and the break is three blocks tall, so the line
        // is a block and a half below the end of period 4 — not a third of the way down the page.
        assertEquals(21.5, TimetableLayout.blockOf(13, 0), absoluteTolerance = 0.01)
    }

    @Test
    fun aClassIsOnlySplitByTheClockWhenItIsToday() {
        val monday = LocalDate(2025, 9, 1)
        val tuesday = LocalDate(2025, 9, 2)
        // Period 1 to 2 is blocks 0..10.
        val nineOClock = 9 * 60

        assertEquals(
            10.0,
            TimetableLayout.completedBlocks(0.0, 10.0, monday, tuesday, nineOClock),
            "A class on a past day is over whatever the clock says.",
        )
        assertEquals(
            0.0,
            TimetableLayout.completedBlocks(0.0, 10.0, tuesday, monday, nineOClock),
            "A class on a future day has not started.",
        )
        assertEquals(
            0.0,
            TimetableLayout.completedBlocks(0.0, 10.0, monday, monday, 8 * 60),
            "Before its first bell, none of it is over.",
        )
        assertEquals(
            10.0,
            TimetableLayout.completedBlocks(0.0, 10.0, monday, monday, 23 * 60),
            "And after its last one, all of it is.",
        )
    }

    @Test
    fun aClassInProgressIsSplitWhereTheClockIsNotWhereTheBlockEnds() {
        // 09:30 is ten minutes into period 2 (09:20–10:05 → blocks 5..10), so the split lands just
        // inside the second period rather than on the boundary between the two.
        val split = TimetableLayout.completedBlocks(
            blockStart = 0.0,
            blockStop = 10.0,
            day = LocalDate(2025, 9, 1),
            today = LocalDate(2025, 9, 1),
            nowMinuteOfDay = 9 * 60 + 30,
        )

        assertTrue(split > 5.0, "Period 1 is behind us by 09:30, so the split is past block 5: $split")
        assertTrue(split < 10.0, "And period 2 has not finished: $split")
    }
}
