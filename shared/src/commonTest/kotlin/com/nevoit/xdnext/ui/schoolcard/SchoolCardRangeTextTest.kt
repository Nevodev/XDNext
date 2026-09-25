package com.nevoit.xdnext.ui.schoolcard

import com.nevoit.xdnext.data.schoolcard.SchoolCardRange
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the page's wording.
 *
 * Both functions are pure so that the three places a range is named — the summary's label, the button
 * that opens the picker, and the picker's own banner — cannot drift into three spellings of the same
 * days, and so a change to the wording is a change a test sees rather than a change a screenshot might.
 */
class SchoolCardRangeTextTest {

    private val today = LocalDate(2026, 3, 5)

    @Test
    fun writesADayOnceWithItsYear() {
        // A single day is standing on its own, so it says which year it is in: the page can be opened on
        // a day that is not today, and 3月5日 alone would leave the year to be inferred.
        assertEquals("2026年3月5日", formatRangeText(SchoolCardRange(today, today)))
        assertEquals("2025年12月31日", formatRangeText(SchoolCardRange(LocalDate(2025, 12, 31), LocalDate(2025, 12, 31))))
    }

    @Test
    fun dropsTheYearFromARangeInsideOneYear() {
        // The same year from both ends is the ordinary case, and four characters of year in front of each
        // date pushes the part that matters off the line.
        assertEquals(
            "3月1日 - 3月5日",
            formatRangeText(SchoolCardRange(LocalDate(2026, 3, 1), today)),
        )
        assertEquals(
            "3月5日 - 3月31日",
            formatRangeText(SchoolCardRange(today, LocalDate(2026, 3, 31))),
        )
    }

    @Test
    fun keepsBothYearsWhenTheRangeStraddlesNewYear() {
        // Here the years are the point: without them, 12月30日 - 1月2日 reads as a range that goes
        // backwards.
        assertEquals(
            "2025年12月30日 - 2026年1月2日",
            formatRangeText(SchoolCardRange(LocalDate(2025, 12, 30), LocalDate(2026, 1, 2))),
        )
    }

    @Test
    fun namesTheRangeTheWayTheFigureHasToBeRead() {
        // The card leads with a number and no other label, so this line is what says whether it is today's
        // spending — the figure the campus tile shows — or a window's.
        assertEquals("今日支出", rangeLabel(SchoolCardRange(today, today), today))
        assertEquals("当日支出", rangeLabel(SchoolCardRange(LocalDate(2026, 3, 4), LocalDate(2026, 3, 4)), today))
        assertEquals("本区间支出", rangeLabel(SchoolCardRange(LocalDate(2026, 3, 1), today), today))
    }
}
