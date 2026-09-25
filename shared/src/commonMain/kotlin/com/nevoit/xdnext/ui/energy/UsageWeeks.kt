package com.nevoit.xdnext.ui.energy

import com.nevoit.xdnext.data.energy.MeterInfo
import com.nevoit.xdnext.ui.home.formatDateTitle
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * The data behind the usage chart: one week per page, Monday first.
 *
 * The screen's chart is a page of a horizontal pager rather than the list of horizontal bars the
 * reference drew (`electricity_average_usage_graph.dart`): seven days side by side are one week, and
 * a week is the unit the user actually compares — this Monday against last Monday. Everything in
 * this file is a pure function of the readings, so the arithmetic the chart depends on is pinned by
 * `UsageWeeksTest` instead of by looking at a screenshot.
 */

/**
 * One calendar day's usage, after the meter's rows for that day have been merged.
 *
 * The reference's `DailyElectricityUsage`, and the day boundary is the same: the meter reports whole
 * days, so this is that day, not the moment of the read.
 */
internal data class DayUsage(val date: LocalDate, val value: Double)

/**
 * One page of the usage chart: the seven days from Monday to Sunday.
 *
 * [days] always holds seven entries, and a **null** is a day the meter did not report. Absent is not
 * zero: a day that was never read says nothing about how much was used, and counting it as `0` would
 * both drag the average down and mark every reported day as "above average". The chart draws nothing
 * there, which is what the reference did with a day it had no row for too.
 */
internal data class UsageWeek(
    val monday: LocalDate,
    val days: List<DayUsage?>,
) {

    /** The days the meter did report, in week order. */
    val readDays: List<DayUsage> get() = days.filterNotNull()

    /** The week's total over the read days — the negative corrections the meter reports included. */
    val total: Double get() = readDays.sumOf { it.value }

    /**
     * The "日均" the card prints under its title, or null for a week with no readings at all.
     *
     * The divisor is the number of **read** days rather than seven, for the reason above. The chart
     * draws the same number as its dashed line, so one week has one average and both the line and the
     * title come from it.
     */
    val average: Double? get() = readDays.takeIf { it.isNotEmpty() }?.let { total / it.size }

    /** The week's highest day, which is what this page's own value axis is scaled to. */
    val peak: Double? get() = readDays.maxOfOrNull { it.value }

    val sunday: LocalDate get() = monday.plus(DAYS_IN_WEEK - 1, DateTimeUnit.DAY)
}

/**
 * The weeks the readings cover, oldest first, one entry per calendar week.
 *
 * Every week **between** the first and the last reading is included, even one with no readings at
 * all. The pager is a time axis: dropping a silent week would make a swipe jump an unknown distance
 * and would quietly turn "the week before last" into "last week", which is the one thing a pager of
 * weeks must not do.
 *
 * An empty week still has its seven slots and no bars, which is the honest picture of a meter that
 * reported nothing that week.
 */
internal fun usageWeeks(rows: List<MeterInfo>): List<UsageWeek> {
    val days = dailyUsage(rows)
    if (days.isEmpty()) return emptyList()

    val firstMonday = days.first().date.mondayOfWeek()
    val lastMonday = days.last().date.mondayOfWeek()
    val weekCount = ((lastMonday.toEpochDays() - firstMonday.toEpochDays()) / DAYS_IN_WEEK).toInt() + 1
    val byDate = days.associateBy { it.date }

    return List(weekCount) { index ->
        val monday = firstMonday.plus(index * DAYS_IN_WEEK, DateTimeUnit.DAY)
        UsageWeek(
            monday = monday,
            days = List(DAYS_IN_WEEK) { offset -> byDate[monday.plus(offset, DateTimeUnit.DAY)] },
        )
    }
}

/**
 * Merges the meter's rows down to one value per calendar day, oldest first.
 *
 * Two merges, both from the reference and both deliberate:
 *
 *  - the same interval can come back more than once, once per read timestamp, and must not be counted
 *    twice — hence the de-duplication on the interval itself rather than on the row;
 *  - a negative `ReadNum` is a meter correction, and it **offsets** the positive rows of the same day
 *    rather than being dropped, which is why this sums a signed value.
 */
internal fun dailyUsage(rows: List<MeterInfo>): List<DayUsage> =
    rows.groupBy { it.readTime }
        .toSortedMap()
        .map { (date, values) ->
            DayUsage(
                date = date,
                value = values
                    .distinctBy { Triple(it.startNum, it.endNum, it.readNum) }
                    .sumOf { it.readNum },
            )
        }

/**
 * Whether [day] used more than its week's average — the one distinction the bars still paint.
 *
 * A day exactly on the average is *not* above it, so the boundary day keeps the plain colour: the
 * average is a floor, not a target. A week that was never read has no average for anything to be above,
 * and answering that here is what keeps the call site from having to know it.
 */
internal fun UsageWeek.isAboveAverage(day: DayUsage): Boolean = average?.let { day.value > it } == true

/**
 * The chart's value axis: a rounded step, the number of gaps it is drawn in, and the labels that
 * follow from the two.
 *
 * Each page builds its own, from its own week's [UsageWeek.peak], and carries its own labels — see
 * `UsageWeekPage`. A scale shared by the whole window would be sized by its busiest week, which is
 * what turned every quiet week into a row of stubs; the price is that two pages are compared by
 * shape rather than by bar height, which the printed numbers pay for.
 */
internal data class UsageAxis(val step: Double, val intervals: Int) {

    /** The top of the axis: the last gridline, and the value a full-height bar means. */
    val max: Double get() = step * intervals

    /** The label of gridline [index], counting up from the zero line. */
    fun label(index: Int): String = formatUsageValue(step * index, decimals = decimalsOf(step))

    /** Where [value] sits on the axis, 0 at the baseline and 1 at [max]. */
    fun fraction(value: Double): Float = (value / max).toFloat()
}

/**
 * The axis that fits [peak] — the tallest day of the week on the page — in [intervals] gaps.
 *
 * The step is the first candidate at the right magnitude, so the labels read `0 / 2.5 / 5 / 7.5 / 10`
 * and never `0 / 2.13 / 4.26`. Below a magnitude of one the halves are dropped, because `1.5` there
 * is a step of `0.15` and `0.15 / 0.3 / 0.45` is a worse axis than the coarser, printable
 * `0.2 / 0.4 / 0.6`. A peak of zero or less has nothing to scale to and falls back to a plain
 * 0-to-4 axis, which draws as an empty grid.
 */
internal fun usageAxis(peak: Double, intervals: Int = USAGE_AXIS_INTERVALS): UsageAxis {
    require(intervals > 0) { "an axis needs at least one interval but was asked for $intervals" }

    val raw = peak / intervals
    if (!raw.isFinite() || raw <= 0.0) return UsageAxis(step = 1.0, intervals = intervals)

    val magnitude = 10.0.pow(floor(log10(raw)))
    val normalized = raw / magnitude
    val candidates = if (magnitude >= 1.0) NICE_STEPS else NICE_STEPS_BELOW_ONE
    val nice = candidates.firstOrNull { it >= normalized - ROUNDING_SLACK } ?: candidates.last()
    return UsageAxis(step = nice * magnitude, intervals = intervals)
}

/**
 * A kWh figure as the chart prints it: rounded to [decimals] places, with a trailing ".0" dropped.
 *
 * Rounded rather than truncated, which is what the reference's own `toStringAsFixed` did: 3.29 is
 * *3.3*, and truncation would print the average under the title a tenth low. Ties go away from zero,
 * and the rounding is spelled out as `floor(x + 0.5)` because `kotlin.math.round` is
 * round-half-to-even — 32.5 would become 32, and a number that rounds differently from the one the
 * same figure would get anywhere else is a bug waiting to be reported as "the average is wrong".
 */
internal fun formatUsageValue(value: Double, decimals: Int): String {
    if (!value.isFinite() || decimals < 0) return "0"

    val scale = 10.0.pow(decimals).toLong()
    val scaled = floor(abs(value) * scale + 0.5).toLong()
    if (scaled == 0L) return "0"

    val whole = scaled / scale
    val fraction = (scaled % scale).toString().padStart(decimals, '0').trimEnd('0')
    val sign = if (value < 0) "-" else ""
    return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole.$fraction"
}

/**
 * What the chart calls the week it is showing: 本周, 上周, or the two dates it runs between.
 *
 * This is the card's **title**, so it is the one line that has to answer "which week am I looking
 * at?" on its own — hence the dates rather than a week number for the older pages.
 *
 * [today] is a parameter rather than a clock read inside, so what a given week is called on a given
 * day is a test case instead of something only visible during the week it applies to.
 */
internal fun weekTitle(week: UsageWeek, today: LocalDate): String {
    val weeksAgo = ((today.mondayOfWeek().toEpochDays() - week.monday.toEpochDays()) / DAYS_IN_WEEK).toInt()
    return when (weeksAgo) {
        0 -> "本周"
        1 -> "上周"
        else -> "${formatDateTitle(week.monday)} - ${formatDateTitle(week.sunday)}"
    }
}

/**
 * The line under the week's name: that week's 日均, which is the number this card exists for.
 *
 * Stated rather than left to be read off the dashed line the chart draws for it — and a week with no
 * readings says so instead of printing a zero. The unit is 度, which is the word the home screen's
 * energy tile already uses for a kWh; the two are the same quantity written two ways.
 */
internal fun weekSubtitle(week: UsageWeek): String =
    week.average?.let { "日均 ${formatUsageValue(it, decimals = 1)} 度" } ?: "没有抄表记录"

/** The Monday of this date's Monday-to-Sunday week. */
internal fun LocalDate.mondayOfWeek(): LocalDate =
    LocalDate.fromEpochDays(toEpochDays() - dayOfWeek.ordinal)

/** The letters under the bars: `一` … `日`, one per day, Monday first. */
internal val WeekdayLetters = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * How many decimals a label of this step needs.
 *
 * Read off the step itself rather than picked from a threshold — `10` needs none, `25` none either,
 * `2.5` one and `0.25` two — because a `0.25` step printed at one decimal reads `0 / 0.3 / 0.5 /
 * 0.8 / 1`, which is an axis that does not add up in front of the user.
 */
private fun decimalsOf(step: Double): Int {
    for (decimals in 0..MAX_LABEL_DECIMALS) {
        val scaled = step * 10.0.pow(decimals)
        if (abs(scaled - floor(scaled + 0.5)) < ROUNDING_SLACK) return decimals
    }
    return MAX_LABEL_DECIMALS
}

/** The steps a rounded axis is allowed to take, from a magnitude of one upwards. */
private val NICE_STEPS = listOf(1.0, 1.5, 2.0, 2.5, 5.0, 10.0)

/**
 * The same below one, where a half-step would print two decimals.
 *
 * `2.5` stays: `0.25 / 0.5 / 0.75 / 1` is both a common axis and four characters wide, which is the
 * widest the label column is built for.
 */
private val NICE_STEPS_BELOW_ONE = listOf(1.0, 2.0, 2.5, 5.0, 10.0)

internal const val DAYS_IN_WEEK = 7

/** Four gaps, so five labels: as many as the reference image's 0/10/20/30 axis, one finer. */
private const val USAGE_AXIS_INTERVALS = 4

/** `nice >= normalized` in floating point, without letting a 0.000000001 miss count as a step down. */
private const val ROUNDING_SLACK = 1e-9

/** The most decimals a label may print, which is also the widest the label column has to hold. */
private const val MAX_LABEL_DECIMALS = 2
