package com.nevoit.xdnext.data.experiment

import com.nevoit.xdnext.data.timetable.ClassBlockEntry
import com.nevoit.xdnext.data.timetable.ClassDetail
import com.nevoit.xdnext.data.timetable.ClassTableData
import com.nevoit.xdnext.data.timetable.TimeArrangement
import com.nevoit.xdnext.data.timetable.TimetableGrid
import com.nevoit.xdnext.data.timetable.TimetableOverlay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The whole integration, without a screen: a fetched booking, through the overlay, into a week's cell.
 *
 * This is the part of the module that no unit of it can prove on its own — the timetable's grid, the
 * overlay seam and [experimentDayBlocks] each have their own tests, and this is the one that says the
 * three add up. It is also the closest thing to a smoke run this change can have here: the app is
 * Android-only and no device is attached, so what a rectangle on Thursday looks like is checked as
 * arithmetic rather than as pixels.
 */
class ExperimentGridTest {

    /** 2026-03-02 is a Monday, so the Thursday of that week is 2026-03-05. */
    private val termStart = "2026-03-02"

    private val booking = ExperimentEntry(
        name = "示波器的使用",
        classroom = "实验楼A201",
        timeRanges = listOf(
            ExperimentTimeRange(
                start = LocalDateTime(LocalDate(2026, 3, 5), LocalTime(15, 55)),
                stop = LocalDateTime(LocalDate(2026, 3, 5), LocalTime(18, 10)),
            ),
        ),
        teacher = "张三",
    )

    @Test
    fun aBookingIsDrawnInItsOwnWeeksCell() {
        val grid = gridWith(listOf(booking), courses = emptyList())
        val weekStart = LocalDate(2026, 3, 2)

        val thursday = grid.blocks(weekIndex = 0, dayIndex = 4, weekStart = weekStart)

        assertEquals(1, thursday.size)
        assertEquals("示波器的使用", thursday.single().name)
        assertEquals("实验楼A201", thursday.single().place)
        assertIs<ClassBlockEntry.Experiment>(thursday.single().entries.single())
        assertTrue(
            grid.blocks(weekIndex = 0, dayIndex = 3, weekStart = weekStart).isEmpty(),
            "Wednesday is not where it is.",
        )
        assertTrue(
            grid.blocks(weekIndex = 1, dayIndex = 4, weekStart = LocalDate(2026, 3, 9)).isEmpty(),
            "Nor is the following week.",
        )
    }

    @Test
    fun aBookingThatClashesWithACourseBecomesOneCardHoldingBoth() {
        // The grid's rule for two sources is the one it already has for two courses: one rectangle,
        // named after the larger member, that remembers both. A physics lab in the same slot as a
        // lecture is exactly the case that rule exists for.
        val grid = gridWith(
            entries = listOf(booking),
            courses = listOf(course(period = 8)),
        )
        val weekStart = LocalDate(2026, 3, 2)

        val cell = grid.blocks(weekIndex = 0, dayIndex = 4, weekStart = weekStart).single()

        assertTrue(cell.isMerged)
        assertEquals(2, cell.entryCount)
        assertTrue(cell.entries.any { it is ClassBlockEntry.Experiment }, "The booking is kept.")
        assertTrue(cell.entries.any { it is ClassBlockEntry.Course }, "And so is the course.")
    }

    // --- Fixtures ---------------------------------------------------------------------------------

    private fun gridWith(entries: List<ExperimentEntry>, courses: List<TimeArrangement>) = TimetableGrid(
        data = ClassTableData(
            semesterLength = 2,
            semesterCode = "2025-2026-2",
            termStartDay = termStart,
            classDetail = List(courses.size.coerceAtLeast(1)) { ClassDetail(name = "课程$it") },
            timeArrangement = courses,
        ),
        overlays = listOf(TimetableOverlay { weekStart -> experimentDayBlocks(entries, weekStart) }),
    )

    /** One course on Thursday, in the given period. */
    private fun course(period: Int) = TimeArrangement(
        index = 0,
        weekList = listOf(true),
        day = 4,
        start = period,
        stop = period,
    )
}
