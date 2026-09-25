package com.nevoit.xdnext.data.energy

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the query-window arithmetic.
 *
 * The original built its windows with `package:time`'s `shift`, which forwards to Dart's `DateTime`
 * constructor and therefore *overflows* an out-of-range day into the next month instead of clamping
 * it. `java.time` — and so `LocalDate.plus(DatePeriod(months = -1))` — clamps instead, which would
 * move the start of the electricity window by up to three days. Every row here is a case where the
 * two disagree.
 */
class EnergyCalendarTest {

    @Test
    fun shiftsWithinAMonth() {
        assertEquals(LocalDate(2026, 2, 15), LocalDate(2026, 3, 15).shift(months = -1))
        assertEquals(LocalDate(2025, 12, 15), LocalDate(2026, 1, 15).shift(months = -1))
        assertEquals(LocalDate(2026, 4, 15), LocalDate(2026, 3, 15).shift(months = 1))
        assertEquals(LocalDate(2027, 1, 15), LocalDate(2026, 1, 15).shift(years = 1))
    }

    @Test
    fun overflowsADayThatTheTargetMonthDoesNotHave() {
        // February 2026 has 28 days, so the 31st becomes the 3rd of March — Dart's `DateTime(2026, 2, 31)`.
        assertEquals(LocalDate(2026, 3, 3), LocalDate(2026, 3, 31).shift(months = -1))
        assertEquals(LocalDate(2026, 3, 3), LocalDate(2026, 1, 31).shift(months = 1))
        // 2025 is not a leap year, so shifting 2024-02-29 forward a year lands on 2025-03-01.
        assertEquals(LocalDate(2025, 3, 1), LocalDate(2024, 2, 29).shift(years = 1))
    }

    @Test
    fun handlesTheLastDayOfALeapFebruary() {
        assertEquals(LocalDate(2024, 2, 29), LocalDate(2024, 3, 29).shift(months = -1))
        assertEquals(LocalDate(2027, 3, 28), LocalDate(2026, 2, 28).shift(months = 1, years = 1))
    }

    @Test
    fun crossesYearBoundariesBackwards() {
        assertEquals(LocalDate(2025, 11, 28), LocalDate(2026, 2, 28).shift(months = -3))
        assertEquals(LocalDate(2025, 4, 22), LocalDate(2026, 4, 22).shift(years = -1))
    }

    @Test
    fun countsTheLengthOfEachMonth() {
        assertEquals(31, daysInMonth(2026, 1))
        assertEquals(28, daysInMonth(2026, 2))
        assertEquals(29, daysInMonth(2024, 2))
        assertEquals(30, daysInMonth(2026, 4))
        assertEquals(
            28,
            daysInMonth(1900, 2),
            "1900 is not a leap year: divisible by 100, not by 400."
        )
        assertEquals(29, daysInMonth(2000, 2), "2000 is a leap year: divisible by 400.")
    }
}
