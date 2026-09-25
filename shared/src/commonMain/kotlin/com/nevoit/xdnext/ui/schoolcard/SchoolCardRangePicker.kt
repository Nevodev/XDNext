package com.nevoit.xdnext.ui.schoolcard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nevoit.material.core.component.AlertDialog
import com.nevoit.material.core.component.IconButton
import com.nevoit.material.core.component.Text
import com.nevoit.material.core.component.TextButton
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.xdnext.data.schoolcard.SchoolCardRange
import com.nevoit.xdnext.ui.symbols.Symbol
import com.nevoit.xdnext.ui.symbols.SymbolIcon
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.plus

/**
 * Picks the days the card page lists.
 *
 * The original's `calendar_date_picker2` dialog in the parts that matter: a month with arrows on either
 * side of its name, a grid of days, two taps to choose a window, and the two action buttons under it.
 * The range is only *returned* when 确定 is tapped, so a user who changes their mind leaves the list
 * alone — and, as in the original, selecting the days is a query of its own rather than a filter over an
 * already-fetched month.
 *
 * Two things are deliberately not the original's:
 *
 *  - **days after today are not selectable.** The card system has nothing to say about them, and a window
 *    that reaches into next week would answer with a partial list that looks complete.
 *  - **the pending selection is an anchor plus a range** rather than a list of one or two dates: the first
 *    tap sets the anchor *and* shows the single day, so a one-day window is one tap and 确定 rather than
 *    two taps on the same square.
 *
 * @param initial the range the page is showing, so the dialog opens on the user's current question.
 * @param today the last day that can be chosen, and the day the calendar stops at.
 */
@Composable
internal fun SchoolCardRangePickerDialog(
    initial: SchoolCardRange,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (SchoolCardRange) -> Unit,
) {
    var range by remember(initial) { mutableStateOf(initial) }
    // The first tap of a two-tap selection: null once both ends are chosen, and the days between this
    // and the next tap are what the second tap decides.
    var anchor by remember(initial) { mutableStateOf<LocalDate?>(null) }
    var month by remember(initial) { mutableStateOf(initial.from.firstOfMonth()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择区间") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RangeBanner(range = range, anchorPending = anchor != null)
                MonthCalendar(
                    month = month,
                    today = today,
                    range = range,
                    onPreviousMonth = { month = month.plus(-1, DateTimeUnit.MONTH) },
                    onNextMonth = { month = month.plus(1, DateTimeUnit.MONTH) },
                    onPickDay = { day ->
                        val pending = anchor
                        if (pending == null) {
                            anchor = day
                            range = SchoolCardRange(day, day)
                        } else {
                            anchor = null
                            range = SchoolCardRange.of(pending, day)
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(range) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** The chosen days, and what the next tap will do. */
@Composable
private fun RangeBanner(range: SchoolCardRange, anchorPending: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(formatRangeText(range), style = MaterialTheme.type.subHeadlineEmphasized)
        Text(
            text = if (anchorPending) "再点一天作为结束" else "点选两天，或点一天查当天",
            style = MaterialTheme.type.footnote,
            color = MaterialTheme.colors.contentVariant,
        )
    }
}

/** One month of days, with the arrows that walk to its neighbours. */
@Composable
private fun MonthCalendar(
    month: LocalDate,
    today: LocalDate,
    range: SchoolCardRange,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onPickDay: (LocalDate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onPreviousMonth) {
                SymbolIcon(Symbol.ChevronLeft, contentDescription = "上个月")
            }
            Text(
                text = "${month.year}年${month.month.number}月",
                style = MaterialTheme.type.subHeadlineEmphasized,
            )
            IconButton(
                // A month with no selectable day in it is not worth walking to: the card system has
                // nothing to say about days that have not happened.
                enabled = month.plus(1, DateTimeUnit.MONTH) <= today.firstOfMonth(),
                onClick = onNextMonth,
            ) {
                SymbolIcon(Symbol.Chevron, contentDescription = "下个月")
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            WeekdayNames.forEach { name ->
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.type.footnote,
                    color = MaterialTheme.colors.contentVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        month.weeks().forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    if (day == null) {
                        Box(modifier = Modifier.weight(1f))
                    } else {
                        DayCell(
                            modifier = Modifier.weight(1f),
                            day = day,
                            today = today,
                            range = range,
                            selectable = day <= today,
                            onPick = { onPickDay(day) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One day of the grid: a circle, filled when the day is an end of the range and tinted when it is
 * inside it.
 *
 * Today is written in the accent colour when it is not part of the selection, which is where the eye
 * starts when the question is "what did I spend lately".
 */
@Composable
private fun DayCell(
    modifier: Modifier = Modifier,
    day: LocalDate,
    today: LocalDate,
    range: SchoolCardRange,
    selectable: Boolean,
    onPick: () -> Unit,
) {
    val colors = MaterialTheme.colors
    val isEnd = day == range.from || day == range.to
    val isInside = range.contains(day) && !isEnd

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .clip(CircleShape)
            .background(
                when {
                    isEnd -> colors.primary
                    isInside -> colors.primary.copy(alpha = 0.15f)
                    else -> colors.content.copy(alpha = 0f)
                }
            )
            .then(
                // Today gets a ring of its own so the calendar says where "now" is, which is what a
                // window about this month is usually measured from.
                if (day == today && !isEnd) Modifier.border(1.dp, colors.primary, CircleShape)
                else Modifier
            )
            .clickable(enabled = selectable, onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.day.toString(),
            style = MaterialTheme.type.footnote,
            color = when {
                !selectable -> colors.content.copy(alpha = 0.38f)
                isEnd -> colors.onPrimary
                day == today || isInside -> colors.primary
                else -> colors.content
            },
        )
    }
}

/** The grid's day names, Monday first, which is how `week()` below lays the month out. */
private val WeekdayNames = listOf("一", "二", "三", "四", "五", "六", "日")

/** The first of [this] day's month. */
private fun LocalDate.firstOfMonth(): LocalDate = LocalDate(year, month, 1)

/**
 * The month's days, one row per week, with nulls where a week starts or ends outside the month.
 *
 * Monday-first, matching [WeekdayNames] and `LocalDate.dayOfWeek.isoDayNumber`. Every row is padded to
 * seven cells, which is what keeps the columns under their names and the grid's width steady as the
 * months change.
 */
private fun LocalDate.weeks(): List<List<LocalDate?>> {
    val first = firstOfMonth()
    val daysInMonth = first.daysUntil(first.plus(1, DateTimeUnit.MONTH))
    val lead = first.dayOfWeek.isoDayNumber - 1
    val cells = List<LocalDate?>(lead) { null } +
            (0 until daysInMonth).map { first.plus(it, DateTimeUnit.DAY) }
    return cells.chunked(7).map { week -> week + List(7 - week.size) { null } }
}
