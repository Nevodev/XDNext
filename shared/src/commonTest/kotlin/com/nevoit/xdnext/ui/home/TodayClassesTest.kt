package com.nevoit.xdnext.ui.home

import com.nevoit.xdnext.data.timetable.ClassDetail
import com.nevoit.xdnext.data.timetable.ClassTableData
import com.nevoit.xdnext.data.timetable.TimeArrangement
import com.nevoit.xdnext.data.timetable.TimetableState
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Tests for what the campus page's timetable banner counts and lists.
 *
 * The banner answers a question with a clock in it — which of today's classes are still to come — so the
 * cases worth pinning are the ones a rule of thumb gets wrong: a class that has *started* but not
 * finished is still remaining, a class on another day is not today's whatever the week says, and a date
 * outside the term has no answer rather than an empty timetable's answer.
 *
 * The times are the registrar's own (`TimetableLayout.PeriodTimes`), which is why the expectations are
 * 08:30 and 10:05 rather than anything this file computes.
 */
class TodayClassesTest {

    @Test
    fun listsTodaysClassesInTheOrderTheyStart() {
        val classes = remainingClassesToday(state(), at(hour = 8, minute = 0))

        assertEquals(listOf("高等数学", "大学物理"), classes.map { it.name })
        assertEquals("08:30", classes.first().startText)
        assertEquals("10:05", classes.first().stopText)
    }

    @Test
    fun keepsTheClassThatIsHappeningNow() {
        // Mid-lecture is not "over": the grid still draws the class and its indicator is inside it, so a
        // banner that dropped it would be counting something the user can see.
        val classes = remainingClassesToday(state(), at(hour = 9, minute = 30))

        assertEquals(listOf("高等数学", "大学物理"), classes.map { it.name })
    }

    @Test
    fun dropsAClassOnceItHasFinished() {
        // 10:05 is when the first one ends, so only the afternoon class is left.
        val classes = remainingClassesToday(state(), at(hour = 10, minute = 5))

        assertEquals(listOf("大学物理"), classes.map { it.name })
    }

    @Test
    fun hasNothingLeftAtTheEndOfTheDay() {
        assertTrue(remainingClassesToday(state(), at(hour = 21, minute = 0)).isEmpty())
    }

    @Test
    fun ignoresTheClassesOfOtherDays() {
        // Tuesday's class exists in the timetable and must not appear in Monday's banner, even though it
        // is in the same week and would pass a filter that only checked the week.
        val classes = remainingClassesToday(state(), at(hour = 8, minute = 0))
            .map { it.name }

        assertTrue("线性代数" !in classes)
    }

    @Test
    fun hasNoAnswerBeforeTheTermStarts() {
        // The calendar's `-1` rather than a negative week: a date before the term has no classes to be
        // remaining, which is an empty list — the same answer the page draws, with the count it shows.
        val beforeTerm = LocalDateTime(2026, 2, 23, 8, 0)

        assertTrue(remainingClassesToday(state(), beforeTerm).isEmpty())
    }

    @Test
    fun hasNoAnswerWithoutATimetable() {
        assertTrue(remainingClassesToday(TimetableState(isLoading = false), at(8, 0)).isEmpty())
    }

    @Test
    fun carriesTheCoursesOwnPaletteIndexAndRoom() {
        // The banner paints a class with the palette entry the grid paints it with, which comes from the
        // arrangement's index into the course list — not from its position in today's list.
        val classes = remainingClassesToday(state(), at(hour = 8, minute = 0))

        assertEquals(listOf(0, 1), classes.map { it.paletteIndex })
        assertEquals("B203", classes.first().place)
    }

    // --- Helpers ---------------------------------------------------------------------------------

    /** Monday 2026-03-02 is the term's first day, so week 0 is the week these classes are in. */
    private fun at(hour: Int, minute: Int): LocalDateTime =
        LocalDateTime(2026, 3, 2, hour, minute)

    private fun state(): TimetableState = TimetableState(
        isLoading = false,
        fetchTime = Instant.fromEpochMilliseconds(1),
        data = ClassTableData(
            semesterLength = 16,
            semesterCode = "2025-2026-2",
            termStartDay = "2026-03-02",
            classDetail = listOf(
                ClassDetail(name = "高等数学", code = "MA1001", number = "01"),
                ClassDetail(name = "大学物理", code = "PH1001", number = "02"),
                ClassDetail(name = "线性代数", code = "MA1002", number = "03"),
            ),
            timeArrangement = listOf(
                arrangement(index = 0, day = 1, start = 1, stop = 2, classroom = "B203"),
                arrangement(index = 1, day = 1, start = 5, stop = 6, classroom = "A101"),
                // Another day of the same week: never in today's list.
                arrangement(index = 2, day = 2, start = 1, stop = 2, classroom = "C302"),
            ),
        ),
    )

    private fun arrangement(
        index: Int,
        day: Int,
        start: Int,
        stop: Int,
        classroom: String,
    ) = TimeArrangement(
        index = index,
        weekList = List(16) { true },
        day = day,
        start = start,
        stop = stop,
        classroom = classroom,
    )
}
