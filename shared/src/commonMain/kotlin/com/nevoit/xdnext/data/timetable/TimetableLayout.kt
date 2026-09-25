package com.nevoit.xdnext.data.timetable

import com.nevoit.xdnext.data.timetable.TimetableLayout.blockOf
import com.nevoit.xdnext.data.timetable.TimetableLayout.startBlockOf
import com.nevoit.xdnext.data.timetable.TimetableLayout.stopBlockOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.math.max
import kotlin.math.min

/**
 * One row of the left index column: either a period or a break.
 *
 * [Break]'s label is the word the original's i18n carried for 午休 and 晚休; this app has no i18n
 * layer yet, so it travels with the variant.
 */
sealed interface IndexRow {
    /** A teaching period, 1-based. */
    data class Period(val number: Int) : IndexRow

    /** 午休 or 晚休 — the gap the grid draws three blocks of room for. */
    data class Break(val label: String) : IndexRow
}

/**
 * The shape of the grid: where each period sits, and how a wall-clock time maps onto it.
 *
 * Ported from `lib/model/time_list.dart`, `class_organized_data.dart`'s `transferIndex` and
 * `current_time_indicator.dart`'s `_transferIndex`. The numbers are the original's and none of them
 * are round, because they are not a design decision — they are the timetable the university actually
 * runs, plus the gaps the grid draws between the blocks.
 *
 * ### The block model
 *
 * The grid is a stack of **61 equal-height blocks**, not 11 equal periods. Each period is five blocks;
 * the noon break and the supper break are three each; and a class that starts after a break starts
 * three blocks down rather than at a multiple of five:
 *
 * ```
 * periods 1–4   blocks  0,  5, 10, 15
 * 午休           blocks 20–22
 * periods 5–8   blocks 23, 28, 33, 38
 * 晚休           blocks 43–45
 * periods 9–11  blocks 46, 51, 56
 * ```
 *
 * That is the whole reason a period's position is a function rather than a multiplication, and why
 * [startBlockOf] and [stopBlockOf] are not inverses of one another at the boundaries: period 5 starts
 * at block 23 but period 4 ends at block 20, and the three blocks between them *are* the noon break.
 *
 * `isPhone` in the original also changed the divisor used to turn the screen height into one block —
 * 48 blocks' worth of room on a phone against 61 on a wide screen. That is a *layout* decision and
 * lives with the layout, in `ui.timetable`.
 */
object TimetableLayout {

    /**
     * The start and end of every period, even entries starting one and odd entries ending it.
     *
     * Eleven periods: four in the morning, four in the afternoon, three in the evening. This is the
     * original's `timeList`, byte for byte — it is the only place in the app that knows the university
     * rings its bells at 08:30 on a Monday.
     */
    val PeriodTimes: List<String> = listOf(
        "08:30", "09:15",
        "09:20", "10:05",
        "10:25", "11:10",
        "11:15", "12:00",
        "14:00", "14:45",
        "14:50", "15:35",
        "15:55", "16:40",
        "16:45", "17:30",
        "19:00", "19:45",
        "19:50", "20:35",
        "20:40", "21:25",
    )

    /** How many periods a day has. */
    const val PeriodCount: Int = 11

    /** How many blocks the grid is made of, breaks included. */
    const val TotalBlocks: Int = 61

    /** A period's height: five blocks. */
    const val BlocksPerPeriod: Int = 5

    /** How many blocks the noon break and the supper break each occupy. */
    const val BlocksPerBreak: Int = 3

    /** How many blocks the left-hand index column is divided into: 4 + break + 4 + break + 3. */
    const val IndexRows: Int = 13

    /**
     * The highest number of columns the palette is asked to wrap.
     *
     * The original's `colorList` had sixteen entries and a course's colour was
     * `colorList[detail.index % 16]`. Nothing here needs the number — the palette wraps whatever index
     * it is handed — but the tests pin it so a palette that quietly shrinks is caught.
     */
    const val PaletteSize: Int = 16

    /** Where period [period] (1-based) begins. */
    fun startBlockOf(period: Int): Double = blockIndex(period - 1, isStart = true)

    /** Where period [period] (1-based) ends. */
    fun stopBlockOf(period: Int): Double = blockIndex(period, isStart = false)

    /**
     * The block index for a period boundary.
     *
     * [index] is `period - 1` at a start and `period` at an end, which is the original's own calling
     * convention and the reason the two are not a single call with a flag at the top level.
     */
    private fun blockIndex(index: Int, isStart: Boolean): Double = when {
        // The morning, plus the three-block noon break that follows period 4. A class *starting* at
        // period 4 is unaffected — the break is after it — while period 5 starts on the far side.
        index <= 4 -> index * 5.0 + if (isStart && index == 4) BlocksPerBreak.toDouble() else 0.0
        // The afternoon, plus the supper break after period 8.
        index <= 8 -> index * 5.0 + BlocksPerBreak +
                if (isStart && index == 8) BlocksPerBreak.toDouble() else 0.0
        // The evening, permanently three blocks further down per break, both of which precede it.
        else -> index * 5.0 + 2 * BlocksPerBreak
    }

    /**
     * How many blocks the left index column's row [row] is tall.
     *
     * Thirteen rows, and the two breaks are the short ones — the original's
     * `index != 4 && index != 9 ? 5 : 3`.
     */
    fun indexRowBlocks(row: Int): Int =
        if (row == 4 || row == 9) BlocksPerBreak else BlocksPerPeriod

    /**
     * What the left index column's row [row] stands for.
     *
     * Thirteen rows: four morning periods, the noon break, four afternoon periods, the supper break,
     * three evening periods. The original encoded all three cases in one signed integer — periods
     * zero-based, `-1` for 午休 and `-2` for 晚休 — and then printed `index + 1`, which is why the noon
     * break would have printed as "第 0 节" had anyone asked it to. A sealed result makes the two cases
     * the column actually draws separate, and the caller's `when` has no `else`.
     */
    fun indexRow(row: Int): IndexRow = when (row) {
        in 0..3 -> IndexRow.Period(row + 1)
        4 -> IndexRow.Break("午\n休")
        in 5..8 -> IndexRow.Period(row)
        9 -> IndexRow.Break("晚\n休")
        else -> IndexRow.Period(row - 1)
    }

    /** The minute of the day period [period] starts at, counting from midnight. */
    fun periodStartMinute(period: Int): Int = minuteOfDay(PeriodTimes[(period - 1) * 2])

    /** The minute of the day period [period] ends at. */
    fun periodStopMinute(period: Int): Int = minuteOfDay(PeriodTimes[(period - 1) * 2 + 1])

    /** The wall-clock time period [period] ends at, which is when its classes are over. */
    fun periodStopTime(period: Int): LocalTime =
        runCatching { LocalTime.parse(PeriodTimes[(period - 1) * 2 + 1]) }
            .getOrElse { LocalTime(0, 0) }

    /**
     * Where a wall-clock time falls on the grid.
     *
     * Continuous rather than snapped: the indicator moves down *through* a period and through the
     * breaks, which is what makes it a clock rather than a cursor. The original's function, with its
     * two ends: before the first bell is the top of the grid, after the last one is the bottom.
     *
     * The break formula is the interesting part — the indicator's position during a break is
     * interpolated against the *break's* height, which is why a long lunch moves it a long way and a
     * five-minute gap between periods barely moves it at all.
     */
    fun blockOf(hour: Int, minute: Int): Double {
        if (PeriodTimes.isEmpty()) return 0.0
        val timeInMinutes = hour * 60 + minute
        val firstStart = periodStartMinute(1)
        if (timeInMinutes < firstStart) return 0.0

        for (classIndex in 0 until PeriodCount) {
            val startMinute = periodStartMinute(classIndex + 1)
            val endMinute = periodStopMinute(classIndex + 1)
            val startBlock = startBlockOf(classIndex + 1)
            val endBlock = startBlock + BlocksPerPeriod

            if (timeInMinutes in startMinute until endMinute) {
                val ratio = (timeInMinutes - startMinute).toDouble() / (endMinute - startMinute)
                return startBlock + BlocksPerPeriod * ratio
            }

            if (classIndex == PeriodCount - 1) {
                if (timeInMinutes >= endMinute) return TotalBlocks.toDouble()
                continue
            }

            val nextStartMinute = periodStartMinute(classIndex + 2)
            if (timeInMinutes in endMinute until nextStartMinute) {
                val nextStartBlock = startBlockOf(classIndex + 2)
                val breakMinutes = nextStartMinute - endMinute
                val breakBlocks = nextStartBlock - endBlock
                // A gap with no room drawn for it — an evening break that is empty in this timetable —
                // leaves the indicator at the boundary instead of dividing by zero.
                return if (breakMinutes > 0 && breakBlocks > 0) {
                    val ratio = (timeInMinutes - endMinute).toDouble() / breakMinutes
                    endBlock + breakBlocks * ratio
                } else {
                    endBlock
                }
            }
        }

        return TotalBlocks.toDouble()
    }

    /** [blockOf] for an hour and minute held as a single minute-of-day count. */
    fun blockOfMinuteOfDay(minuteOfDay: Int): Double = blockOf(minuteOfDay / 60, minuteOfDay % 60)

    /** How many blocks a class from [start] to [stop] covers. */
    fun blocksBetween(start: Double, stop: Double): Double = max(0.0, stop - start)

    /**
     * How much of a class is already over.
     *
     * Ported from `_ClassTableViewState._completedHeight`, and geometric on purpose: a class in a past
     * day is entirely done, one in a future day is untouched, and one today is done up to where the
     * clock is. The answer is in **blocks**, not in time, so the caller can scale it by the same block
     * height the card is drawn with — which is what makes the split in the card line up with the
     * indicator's line even though the two are computed from different things.
     */
    fun completedBlocks(
        blockStart: Double,
        blockStop: Double,
        day: LocalDate,
        today: LocalDate,
        nowMinuteOfDay: Int,
    ): Double {
        if (today < day) return 0.0
        if (today > day) return max(0.0, blockStop - blockStart)
        val currentBlock = blockOfMinuteOfDay(nowMinuteOfDay)
        if (currentBlock <= blockStart) return 0.0
        return min(currentBlock - blockStart, max(0.0, blockStop - blockStart))
    }

    /** `"08:30"` as 510. */
    private fun minuteOfDay(hhmm: String): Int {
        val parts = hhmm.split(':')
        if (parts.size != 2) return 0
        val hours = parts[0].trim().toIntOrNull() ?: return 0
        val minutes = parts[1].trim().toIntOrNull() ?: return 0
        return hours * 60 + minutes
    }
}
