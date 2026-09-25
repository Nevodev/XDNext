package com.nevoit.xdnext.data.timetable

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * The arithmetic that turns a term start day into weeks, days and grid positions.
 *
 * Ported from `ClassTableController.getCurrentWeek` and the `startDay` computations scattered through
 * `class_table_view.dart` and `week_choice_view.dart`, gathered here because the same three questions
 * are asked from six places: which week is it, which day does a week start on, and how far into the
 * semester is a given date.
 *
 * Everything here works in [LocalDate]. The original subtracted Dart `DateTime`s that carried a time
 * of day from one that did not and truncated toward zero, which is the same answer for whole dates and
 * one fewer thing to reason about. The one place its own behaviour is reproduced rather than
 * simplified is [currentWeek]'s "`-1` before the term starts", which the page reads as "not in term".
 */
object TimetableCalendar {

    /**
     * The term start day the registrar reported, or null when it is absent or unreadable.
     *
     * eHall answers `XQKSRQ` as a bare date and the postgraduate system as `"yyyy-MM-dd hh:mm:ss"`, so
     * the leading ten characters are the date in both. The original called `DateTime.parse` on the
     * whole thing, which accepts either.
     */
    fun termStartDay(raw: String): LocalDate? {
        if (raw.length < 10) return null
        return runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
    }

    /**
     * The teaching week [today] falls in, counting from zero, or `-1` when the term has not started.
     *
     * The original's own rule, including its sentinel: a date before the term begins answers `-1`
     * rather than a negative week number, because "before term" is one state and not an unbounded
     * family of them. A date after the last week keeps counting — the page clamps that against the
     * semester length, which is the only place the length is known.
     */
    fun currentWeek(termStart: LocalDate, today: LocalDate): Int {
        var delta = today.toEpochDays() - termStart.toEpochDays()
        if (delta < 0) delta = -7
        return (delta / 7).toInt()
    }

    /**
     * The first day of [weekIndex], which is the day the grid's first column is dated from.
     *
     * [weekIndex] is zero-based, so week 0 starts on the term start day itself — and the registrar
     * reports that as the first *teaching* day, not necessarily a Monday. The grid dates its seven
     * columns from it and labels each one with the weekday that date really falls on, exactly as the
     * original did: a term that starts on a Wednesday begins its row with 周三, and its last two
     * columns land in the following calendar week. The column a class is drawn in is decided by the
     * server's `SKXQ` day number, not by this date.
     */
    fun weekStart(termStart: LocalDate, weekIndex: Int): LocalDate =
        termStart.plus(7 * weekIndex, DateTimeUnit.DAY)

    /** The day [dayIndex] (1..7, Monday first) of [weekIndex]. */
    fun dayOf(termStart: LocalDate, weekIndex: Int, dayIndex: Int): LocalDate =
        weekStart(termStart, weekIndex).plus(dayIndex - 1, DateTimeUnit.DAY)
}
