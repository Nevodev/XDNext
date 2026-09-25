package com.nevoit.xdnext.data.energy

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/**
 * Shifts a date by whole years and months, with the overflow behaviour of Dart's `DateTime`.
 *
 * The original built its query windows with `date.shift(months: -1)` / `.shift(years: -1)` from
 * `package:time`. That package's `shift` is not the clamping arithmetic `java.time` implements — it
 * forwards to the `DateTime` constructor, whose out-of-range day **overflows into the following
 * month**:
 *
 * | original | `shift(months: -1)` |
 * |---|---|
 * | 2026-03-31 | 2026-03-03 (February has no 31st, so two days spill over) |
 * | 2026-03-15 | 2026-02-15 |
 * | 2026-01-15 | 2025-12-15 |
 *
 * Reproduced exactly rather than approximated: `plus(DatePeriod(months = -1))` would clamp 2026-03-31
 * to 2026-02-28 instead. The difference is a couple of days on the *start* of a one-month query
 * window, which is why it is documented rather than silently "fixed".
 */
internal fun LocalDate.shift(years: Int = 0, months: Int = 0): LocalDate {
    val totalMonths = year * 12 + (month.number - 1) + months + years * 12
    val targetYear = if (totalMonths >= 0) totalMonths / 12 else -((-totalMonths + 11) / 12)
    var currentYear = targetYear
    var currentMonth = totalMonths - targetYear * 12 + 1
    var currentDay = day

    while (currentDay > daysInMonth(currentYear, currentMonth)) {
        currentDay -= daysInMonth(currentYear, currentMonth)
        currentMonth++
        if (currentMonth > 12) {
            currentMonth = 1
            currentYear++
        }
    }

    return LocalDate(currentYear, currentMonth, currentDay)
}

/** Days in [month] of [year], with the Gregorian leap rule. */
internal fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeapYear(year)) 29 else 28
    else -> throw IllegalArgumentException("month must be in 1..12 but was $month")
}

private fun isLeapYear(year: Int): Boolean =
    year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
