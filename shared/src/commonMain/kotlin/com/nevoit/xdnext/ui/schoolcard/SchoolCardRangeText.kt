package com.nevoit.xdnext.ui.schoolcard

import com.nevoit.xdnext.data.schoolcard.SchoolCardRange
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/**
 * How a range reads on screen: `3月5日`, `3月1日 - 3月5日`, `2025年12月30日 - 2026年1月2日`.
 *
 * Pure functions of the range rather than of a clock, so the wording is pinned by a test instead of by
 * looking at a screenshot — and so the picker's own label, the page's heading and the button that opens
 * it cannot drift into three spellings of the same days.
 *
 * The year is written only when the range needs it. A window inside one year is how a person thinks
 * about "these few days", and four characters of year in front of every date pushes the part that
 * matters — which days — off the line.
 */
internal fun formatRangeText(range: SchoolCardRange): String =
    if (range.isSingleDay) {
        formatRangeDay(range.from, withYear = true)
    } else if (range.from.year == range.to.year) {
        "${formatRangeDay(range.from, withYear = false)} - ${formatRangeDay(range.to, withYear = false)}"
    } else {
        "${formatRangeDay(range.from, withYear = true)} - ${formatRangeDay(range.to, withYear = true)}"
    }

/**
 * The words over the list: 今日 when the range is the one day the tile counts, 当日 for a single earlier
 * day, 本区间 for anything wider.
 *
 * The page's card leads with a total, and the total is only a number: this is what says whether it is
 * today's spending or a window's. The 今日 case is not a special case of the other two — it is the same
 * figure the campus tile shows, named the way the tile names it.
 */
internal fun rangeLabel(range: SchoolCardRange, today: LocalDate): String = when {
    !range.isSingleDay -> "本区间支出"
    range.from == today -> "今日支出"
    else -> "当日支出"
}

/** One day of a range, in the short form a Chinese date is written in. */
private fun formatRangeDay(date: LocalDate, withYear: Boolean): String {
    val month = date.month.number
    return if (withYear) "${date.year}年${month}月${date.day}日" else "${month}月${date.day}日"
}
