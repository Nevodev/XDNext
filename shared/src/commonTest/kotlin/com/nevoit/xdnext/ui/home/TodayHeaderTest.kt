package com.nevoit.xdnext.ui.home

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the campus page's header text.
 *
 * The dates are chosen where a plausible implementation goes wrong: a single-digit month and day (the
 * leading zero), and the week's two ends, because `dayOfWeek` is zero-based in `java.time` and
 * one-based in `kotlinx-datetime`, and mixing the two shifts every weekday by a day.
 */
class TodayHeaderTest {

    @Test
    fun writesTheDateWithoutLeadingZeros() {
        assertEquals("3月5日", formatDateTitle(LocalDate(2026, 3, 5)))
        assertEquals("12月31日", formatDateTitle(LocalDate(2026, 12, 31)))
        assertEquals("1月1日", formatDateTitle(LocalDate(2027, 1, 1)))
    }

    @Test
    fun namesTheWeekdayAtBothEndsOfTheWeek() {
        // 2026-03-02 is a Monday and 2026-03-08 the Sunday that closes the same week.
        assertEquals("周一", formatWeekday(LocalDate(2026, 3, 2)))
        assertEquals("周日", formatWeekday(LocalDate(2026, 3, 8)))

        assertEquals("周二", formatWeekday(LocalDate(2026, 3, 3)))
        assertEquals("周三", formatWeekday(LocalDate(2026, 3, 4)))
        assertEquals("周四", formatWeekday(LocalDate(2026, 3, 5)))
        assertEquals("周五", formatWeekday(LocalDate(2026, 3, 6)))
        assertEquals("周六", formatWeekday(LocalDate(2026, 3, 7)))
    }

    @Test
    fun showsTheWeekNumberOneBased() {
        // The reference counts weeks from zero but displays them from one, so the first week of term is
        // a `currentWeek` of 0 — see `formatWeekOfTerm`.
        assertEquals("第 1 周", formatWeekOfTerm(0))
        assertEquals("第 3 周", formatWeekOfTerm(2))
    }
}
