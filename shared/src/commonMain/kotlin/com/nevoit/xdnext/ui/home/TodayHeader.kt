package com.nevoit.xdnext.ui.home

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/**
 * The campus page's header text: today's date as the title, today's weekday as the subtitle.
 *
 * Both are pure functions of a [LocalDate] rather than of a clock, so the wording is pinned by
 * `TodayHeaderTest` instead of by looking at a screenshot — a header that is right on a Thursday and
 * wrong on a Sunday is exactly the kind of thing that survives review.
 *
 * No leading zero in the month and day ("3月5日", not "03月05日"), which is how a Chinese date is
 * written.
 */
internal fun formatDateTitle(date: LocalDate): String = "${date.month.number}月${date.day}日"

/**
 * The weekday in the original's own wording: 周一 … 周日.
 *
 * Taken from the reference's `weekday.*` strings in `assets/flutter_i18n/zh_CN.yaml`, because the
 * weekday appears beside class information all over that app and the two should read the same. It is
 * the long form (星期一) that is *not* used, which is why this is not spelled out from the locale.
 */
internal fun formatWeekday(date: LocalDate): String = when (date.dayOfWeek) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}

/**
 * The week-of-term line the campus page used to show, kept as the reference for when the timetable
 * arrives.
 *
 * The original's week number is **not** computed from a local rule. It is
 * `(time - startDay).inDays ~/ 7`, where `startDay` is the term start day the *timetable backend*
 * returned (`classTableData.termStartDay`, `classtable_controller.dart:194`) shifted by a persisted
 * user correction:
 *
 * ```
 * startDay = DateTime.parse(termStartDay) + Duration(days: 7 * weekSwift)   // WeekSwiftController
 * currentWeek = (time - startDay).inDays ~/ 7                               // getCurrentWeek
 * ```
 *
 * `weekSwift` (`week_swift_controller.dart`) is the user's own realignment, in weeks, and it is reset
 * to 0 whenever the semester changes — the school's start day is authoritative, and the correction
 * exists only for the case where it is wrong.
 *
 * Two details of the original are worth keeping when this is ported:
 *
 *  - the clamp `if (delta < 0) delta = -7`, so a date *before* the start day reads as week 0 rather
 *    than as a negative week;
 *  - the number is displayed **1-based** (`"${currentWeek + 1}"`), while the zero-based value is what
 *    indexes the timetable's per-week lists. A header that shows 1 for the first week is reading a
 *    `currentWeek` of 0.
 *
 * That is why nothing here derives a week: this app has no timetable module, so there is no
 * `termStartDay` to count from and any local guess would be a second, disagreeing source. The page
 * shows the date and the weekday until the timetable's own field exists.
 */
internal fun formatWeekOfTerm(currentWeek: Int): String = "第 ${currentWeek + 1} 周"
