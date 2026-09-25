package com.nevoit.xdnext.data.timetable

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Tests for the two small pieces of arithmetic that turn a semester code and a date into a week. */
class TimetableCalendarTest {

    private val termStart = LocalDate(2025, 9, 1)

    @Test
    fun readsATermStartDayFromEitherRegistrar() {
        // eHall answers a bare date; the postgraduate system answers a timestamp. The original parsed
        // both with `DateTime.parse`, and both begin with the date.
        assertEquals(LocalDate(2025, 9, 1), TimetableCalendar.termStartDay("2025-09-01"))
        assertEquals(LocalDate(2025, 9, 1), TimetableCalendar.termStartDay("2025-09-01 00:00:00"))
    }

    @Test
    fun answersNothingForATermStartDayItCannotRead() {
        assertNull(TimetableCalendar.termStartDay(""))
        assertNull(TimetableCalendar.termStartDay("2025"))
        assertNull(TimetableCalendar.termStartDay("not a date"))
    }

    @Test
    fun theTermStartsInWeekZero() {
        assertEquals(0, TimetableCalendar.currentWeek(termStart, LocalDate(2025, 9, 1)))
        assertEquals(0, TimetableCalendar.currentWeek(termStart, LocalDate(2025, 9, 7)))
        assertEquals(1, TimetableCalendar.currentWeek(termStart, LocalDate(2025, 9, 8)))
        assertEquals(4, TimetableCalendar.currentWeek(termStart, LocalDate(2025, 9, 29)))
    }

    @Test
    fun beforeTheTermEverythingIsWeekMinusOne() {
        // The original's sentinel: "the term has not started" is one state, not an unbounded family of
        // negative week numbers. The page reads -1 as "not in term" and clamps the week it shows.
        assertEquals(-1, TimetableCalendar.currentWeek(termStart, LocalDate(2025, 8, 31)))
        assertEquals(-1, TimetableCalendar.currentWeek(termStart, LocalDate(2025, 7, 1)))
    }

    @Test
    fun aWeekStartsOnTheDayTheTermDoes() {
        assertEquals(LocalDate(2025, 9, 1), TimetableCalendar.weekStart(termStart, 0))
        assertEquals(LocalDate(2025, 9, 15), TimetableCalendar.weekStart(termStart, 2))
        assertEquals(LocalDate(2025, 9, 17), TimetableCalendar.dayOf(termStart, 2, 3))
    }
}
