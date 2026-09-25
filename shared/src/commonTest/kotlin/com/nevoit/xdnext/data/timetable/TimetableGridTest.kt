package com.nevoit.xdnext.data.timetable

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for how a week's cells are filled.
 *
 * The rules worth pinning are the ones that decide *how many cards* a day shows. A timetable that
 * draws one card per class looks right until two classes clash, and one that merges too eagerly draws
 * two adjacent classes as a single card claiming both.
 */
class TimetableGridTest {

    private val termStart = LocalDate(2025, 9, 1)

    @Test
    fun aCellHoldsOnlyTheClassesOfItsOwnWeekAndDay() {
        val grid = gridOf(
            arrangement(detail = 0, day = 1, start = 1, stop = 2, weeks = listOf(true, false)),
            arrangement(detail = 0, day = 2, start = 1, stop = 2, weeks = listOf(true, false)),
            arrangement(detail = 0, day = 1, start = 1, stop = 2, weeks = listOf(false, true)),
        )

        assertEquals(1, grid.blocks(0, 1, weekStart(0)).size, "Only the first arrangement is week 0 Monday.")
        assertEquals(1, grid.blocks(0, 2, weekStart(0)).size)
        assertEquals(1, grid.blocks(1, 1, weekStart(1)).size, "Week 1's own arrangement.")
        assertEquals(0, grid.blocks(1, 2, weekStart(1)).size, "Nothing on Tuesday of week 1.")
        assertEquals(
            0,
            grid.blocks(9, 1, weekStart(9)).size,
            "A week past the end of every week list is empty, not an error.",
        )
    }

    @Test
    fun clashingClassesBecomeOneCardThatKnowsHowManyItHolds() {
        val grid = gridOf(
            arrangement(detail = 0, day = 1, start = 1, stop = 2, weeks = listOf(true)),
            arrangement(detail = 1, day = 1, start = 1, stop = 4, weeks = listOf(true)),
        )

        val block = grid.blocks(0, 1, weekStart(0)).single()

        assertEquals(2, block.entryCount)
        assertTrue(block.isMerged)
        assertEquals(TimetableLayout.startBlockOf(1), block.start)
        assertEquals(TimetableLayout.stopBlockOf(4), block.stop, "The merged card spans both.")
        assertEquals("Longer", block.name, "And is named after the larger of the two.")
    }

    @Test
    fun twoClassesBackToBackAreNotAClash() {
        // A class ending at period 2 and another starting at period 3 touch, and the original's overlap
        // test says touching is not overlapping. Merging them would put one card where there are two.
        val grid = gridOf(
            arrangement(detail = 0, day = 1, start = 1, stop = 2, weeks = listOf(true)),
            arrangement(detail = 1, day = 1, start = 3, stop = 4, weeks = listOf(true)),
        )

        assertEquals(2, grid.blocks(0, 1, weekStart(0)).size)
    }

    @Test
    fun theNoonBreakLeavesAGapRatherThanJoiningTheTwoHalvesOfADay() {
        // Periods 1–4 end at block 20 and period 5 begins at block 23. The three blocks between them
        // are 午休, and the two must not be drawn as one card spanning it.
        val grid = gridOf(
            arrangement(detail = 0, day = 1, start = 1, stop = 4, weeks = listOf(true)),
            arrangement(detail = 1, day = 1, start = 5, stop = 5, weeks = listOf(true)),
        )

        val blocks = grid.blocks(0, 1, weekStart(0))
        assertEquals(2, blocks.size)
        assertEquals(TimetableLayout.startBlockOf(5), blocks.last().start)
        assertEquals(
            3.0,
            blocks.last().start - blocks.first().stop,
            "The gap is exactly the noon break.",
        )
    }

    @Test
    fun aCardIsColouredByTheCoursesOwnIndex() {
        val grid = gridOf(
            arrangement(detail = 2, day = 1, start = 1, stop = 2, weeks = listOf(true)),
        )

        assertEquals(2, grid.blocks(0, 1, weekStart(0)).single().paletteIndex)
    }

    @Test
    fun anOverlayAddsBlocksToTheSameGrid() {
        // This is the seam the exams, the experiments and the locally added courses arrive through, so
        // what matters is that an overlay's block is drawn beside a course's rather than instead of it.
        val overlay = TimetableOverlay { weekStart ->
            if (weekStart == weekStart(0)) {
                listOf(
                    DayBlock(
                        day = 1,
                        block = ClassBlock.ofTimed(
                            entry = ClassBlockEntry.Course(detail(9), arrangement(detail = 9, day = 1, start = 1, stop = 1, weeks = listOf(true))),
                            name = "考试",
                            place = "B101",
                            paletteIndex = 9,
                            startTime = kotlinx.datetime.LocalTime(9, 0),
                            stopTime = kotlinx.datetime.LocalTime(11, 0),
                        ),
                    ),
                )
            } else {
                emptyList()
            }
        }

        val grid = TimetableGrid(
            ClassTableData(
                semesterLength = 2,
                semesterCode = "2025-2026-1",
                termStartDay = "2025-09-01",
                classDetail = List(10) { detail(it) },
                timeArrangement = listOf(
                    arrangement(detail = 0, day = 1, start = 5, stop = 6, weeks = listOf(true, true)),
                ),
            ),
            listOf(overlay),
        )

        val monday = grid.blocks(0, 1, weekStart(0))
        assertEquals(listOf("考试", "A0"), monday.map { it.name }, "Sorted by where they start.")
        assertEquals(0, grid.blocks(0, 2, weekStart(0)).size)
        assertEquals(1, grid.blocks(1, 1, weekStart(1)).size, "The overlay only claims its own week.")
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private fun weekStart(index: Int) = TimetableCalendar.weekStart(termStart, index)

    private fun detail(index: Int): ClassDetail = ClassDetail(
        name = if (index == 1) "Longer" else "A$index",
        code = "C$index",
    )

    private fun arrangement(
        detail: Int,
        day: Int,
        start: Int,
        stop: Int,
        weeks: List<Boolean>,
    ): TimeArrangement = TimeArrangement(
        index = detail,
        weekList = weeks,
        day = day,
        start = start,
        stop = stop,
    )

    private fun gridOf(vararg arrangements: TimeArrangement): TimetableGrid {
        val highestDetail = arrangements.maxOf { it.index }
        return TimetableGrid(
            ClassTableData(
                semesterLength = 16,
                semesterCode = "2025-2026-1",
                termStartDay = "2025-09-01",
                classDetail = List(highestDetail + 1) { detail(it) },
                timeArrangement = arrangements.toList(),
            ),
        )
    }
}
