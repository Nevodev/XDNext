package com.nevoit.xdnext.ui.energy

import com.nevoit.xdnext.data.energy.MeterInfo
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the usage chart's arithmetic.
 *
 * The chart itself is a screenshot away from being wrong in a way nobody notices — a bar that is one
 * day off, an average divided by seven when only three days were read, an axis whose labels read
 * `0 / 2.13 / 4.26`. None of those are visible in a review, so the week, the average, the peak each
 * page is scaled to, and the axis itself are all asserted here.
 *
 * The week the dates live in is asserted at the top of the first test rather than assumed:
 * `2026-03-02` is a Monday, which is the premise every other case rests on.
 */
class UsageWeeksTest {

    @Test
    fun mergesEachDayIntoOneBar() {
        assertEquals(DayOfWeek.MONDAY, LocalDate(2026, 3, 2).dayOfWeek)

        val days = dailyUsage(
            listOf(
                // Two intervals of the same day: one bar, both intervals.
                read("2026-03-02", usage = 2.0, start = 100.0),
                read("2026-03-02", usage = 3.0, start = 102.0),
                // The same interval reported twice, as the server does when the read time differs.
                read("2026-03-02", usage = 2.0, start = 100.0),
                // A correction: it offsets the day rather than being dropped.
                read("2026-03-02", usage = -1.0, start = 105.0),
                read("2026-03-01", usage = 1.0, start = 99.0),
            ),
        )

        assertEquals(
            listOf(DayUsage(LocalDate(2026, 3, 1), 1.0), DayUsage(LocalDate(2026, 3, 2), 4.0)),
            days,
        )
    }

    @Test
    fun startsWeeksOnMondayAndEndsThemOnSunday() {
        val weeks = usageWeeks(
            listOf(
                read("2026-03-02", usage = 1.0),
                // The Sunday of the same week, not the Monday of the next one.
                read("2026-03-08", usage = 2.0),
                read("2026-03-09", usage = 3.0),
            ),
        )

        assertEquals(2, weeks.size)
        assertEquals(LocalDate(2026, 3, 2), weeks[0].monday)
        assertEquals(LocalDate(2026, 3, 8), weeks[0].sunday)
        assertEquals(listOf(1.0, null, null, null, null, null, 2.0), weeks[0].days.map { it?.value })
        assertEquals(LocalDate(2026, 3, 9), weeks[1].monday)
        assertEquals(listOf(3.0, null, null, null, null, null, null), weeks[1].days.map { it?.value })
    }

    @Test
    fun keepsTheWeeksBetweenTwoReadingsEvenWhenTheyAreEmpty() {
        // Three weeks apart: the pager must not skip the two weeks in the middle, or a swipe would
        // silently jump from "three weeks ago" to "last week".
        val weeks = usageWeeks(
            listOf(read("2026-03-03", usage = 2.0), read("2026-03-24", usage = 4.0)),
        )

        assertEquals(listOf(LocalDate(2026, 3, 2), LocalDate(2026, 3, 9), LocalDate(2026, 3, 16), LocalDate(2026, 3, 23)), weeks.map { it.monday })
        assertEquals(listOf(1, 0, 0, 1), weeks.map { it.readDays.size })
        assertNull(weeks[1].average, "an empty week has no average to show")
        assertNull(weeks[1].peak)
    }

    @Test
    fun averagesOverTheDaysThatWereReadOnly() {
        val week = usageWeeks(
            listOf(
                read("2026-03-02", usage = 2.0),
                read("2026-03-04", usage = 4.0),
            ),
        ).single()

        assertEquals(6.0, week.total)
        // Six over two read days, not six over seven: a day nobody read is not a day of no usage.
        assertEquals(3.0, week.average)
        assertEquals(4.0, week.peak)
    }

    @Test
    fun hasNoWeeksWhenNothingWasRead() {
        assertTrue(usageWeeks(emptyList()).isEmpty())
    }

    @Test
    fun countsOnlyTheDaysStrictlyAboveTheWeeksAverage() {
        val week = usageWeeks(
            listOf(
                read("2026-03-02", usage = 2.0),
                read("2026-03-03", usage = 3.0),
                read("2026-03-04", usage = 4.0),
            ),
        ).single()

        // Three read days, 9 kWh, so the average is 3.0 — and the day that *is* 3.0 has not crossed the
        // line: the boundary day keeps the plain colour while the 4 kWh day takes the accent.
        assertEquals(3.0, week.average)
        assertEquals(listOf(false, false, true), week.readDays.map { week.isAboveAverage(it) })
    }

    @Test
    fun callsNoDayAboveAverageInAWeekThatWasNeverRead() {
        val silent = usageWeeks(
            listOf(read("2026-02-16", usage = 2.0), read("2026-03-02", usage = 4.0)),
        )[1]

        // Nothing was read, so there is no average to be above: the chart draws that week as an empty
        // grid rather than painting a bar it cannot judge.
        assertNull(silent.average)
        assertEquals(false, silent.isAboveAverage(DayUsage(date = silent.monday, value = 1.0)))
    }

    @Test
    fun statesTheAverageAndThePeakTheChartIsDrawnAgainst() {
        val week = usageWeeks(
            listOf(
                read("2026-03-02", usage = 1.0),
                read("2026-03-03", usage = 5.0),
                read("2026-03-04", usage = 4.0),
                read("2026-03-05", usage = 3.0),
                read("2026-03-06", usage = 3.0),
            ),
        ).single()

        // Five read days, 16 kWh. The average is what the line under the title prints; the peak is
        // what this page's own axis is built from and therefore what a full-height bar means.
        assertEquals(3.2, week.average)
        assertEquals(5.0, week.peak)
        assertEquals(6.0, usageAxis(week.peak!!).max)
    }

    @Test
    fun roundsTheAxisUpToAStepWorthPrinting() {
        // The tallest day is 8.5 kWh: four gaps, so the step is the first round 2.5, and the axis
        // tops out at 10 rather than at an exact 8.5 nobody would put a label on.
        assertEquals(UsageAxis(step = 2.5, intervals = 4), usageAxis(8.5))
        assertEquals(10.0, usageAxis(8.5).max)

        assertEquals(1.0, usageAxis(3.1).step)
        assertEquals(4.0, usageAxis(3.1).max)

        // A step of 1 would leave the tallest bar at three quarters of the height; 15 fits it.
        assertEquals(15.0, usageAxis(42.0).step)

        // Nothing to scale to still needs a grid to draw, and a peak of exactly one step needs no
        // rounding up at all.
        assertEquals(1.0, usageAxis(0.0).step)
        assertEquals(1.0, usageAxis(4.0).step)
        assertEquals(4.0, usageAxis(4.0).max)
    }

    @Test
    fun scalesAQuietWeekToItselfWithLabelsThatStillPrint() {
        // The case the per-week scale exists for: a week of well under a kWh a day. The step drops a
        // magnitude with the peak, and the halves are gone below one, because 0.15 / 0.3 / 0.45 is a
        // worse axis than the coarser 0.2 / 0.4 / 0.6.
        val light = usageAxis(0.5)
        assertEquals(0.2, light.step)
        assertEquals(0.8, light.max)
        assertEquals(listOf("0", "0.2", "0.4", "0.6", "0.8"), (0..4).map { light.label(it) })

        // The one fraction below one that survives, because it both rounds to two places and stays
        // inside the label column.
        val halves = usageAxis(1.0)
        assertEquals(0.25, halves.step)
        assertEquals(listOf("0", "0.25", "0.5", "0.75", "1"), (0..4).map { halves.label(it) })
    }

    @Test
    fun printsAxisLabelsWithoutFloatingPointNoise() {
        val halves = UsageAxis(step = 2.5, intervals = 4)
        assertEquals(listOf("0", "2.5", "5", "7.5", "10"), (0..4).map { halves.label(it) })

        // 0.1 is not a binary fraction: the labels are rounded, so 0.30000000000000004 prints as 0.3.
        val tenths = UsageAxis(step = 0.1, intervals = 4)
        assertEquals(listOf("0", "0.1", "0.2", "0.3", "0.4"), (0..4).map { tenths.label(it) })

        val whole = UsageAxis(step = 5.0, intervals = 3)
        assertEquals(listOf("0", "5", "10", "15"), (0..3).map { whole.label(it) })

        assertEquals(0.5f, halves.fraction(5.0))
    }

    @Test
    fun printsValuesRoundedAndWithoutATrailingZero() {
        assertEquals("3", formatUsageValue(3.0, decimals = 1))
        assertEquals("3.3", formatUsageValue(3.25, decimals = 1))
        assertEquals("0.5", formatUsageValue(0.5, decimals = 2))
        assertEquals("-2.5", formatUsageValue(-2.5, decimals = 1))
        assertEquals("0", formatUsageValue(0.0, decimals = 1))
        assertEquals("0", formatUsageValue(Double.NaN, decimals = 1))
    }

    @Test
    fun namesTheWeekOnScreen() {
        val week = usageWeeks(listOf(read("2026-03-02", usage = 2.0))).single()
        val wednesday = LocalDate(2026, 3, 4)

        assertEquals("本周", weekTitle(week, wednesday))
        assertEquals("上周", weekTitle(week, wednesday.plus(7, DateTimeUnit.DAY)))

        val older = usageWeeks(listOf(read("2026-02-16", usage = 2.0))).single()
        assertEquals("2月16日 - 2月22日", weekTitle(older, wednesday))
    }

    @Test
    fun statesTheWeeksAverageUnderItsName() {
        val week = usageWeeks(
            listOf(read("2026-03-02", usage = 2.0), read("2026-03-03", usage = 4.0)),
        ).single()

        // The week's name is the title above this line, so the line itself is the average and nothing
        // else — in 度, the unit the rest of the page reads in.
        assertEquals("日均 3 度", weekSubtitle(week))

        val empty = usageWeeks(
            listOf(read("2026-02-16", usage = 2.0), read("2026-03-02", usage = 4.0)),
        )[1]
        assertEquals("没有抄表记录", weekSubtitle(empty))
    }

    @Test
    fun keepsASevenDayWeekAndOneLetterPerDay() {
        val week = usageWeeks(listOf(read("2026-03-02", usage = 2.0))).single()

        assertEquals(DAYS_IN_WEEK, week.days.size)
        assertEquals(DAYS_IN_WEEK, WeekdayLetters.size)
        assertTrue(WeekdayLetters.first() == "一" && WeekdayLetters.last() == "日")
    }
}

/**
 * One meter row.
 *
 * [start] is what makes a row a *different* interval from another row of the same day, which is the
 * distinction the de-duplication in [dailyUsage] turns on; `end` follows from it so no test has to
 * write a pair that does not add up.
 */
private fun read(
    date: String,
    usage: Double,
    start: Double = 100.0,
): MeterInfo = MeterInfo(
    readTime = LocalDate.parse(date),
    readNum = usage,
    startNum = start,
    endNum = start + usage,
)
