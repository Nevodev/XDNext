package com.nevoit.xdnext.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.times
import com.kyant.shapes.RoundedRectangle
import com.nevoit.material.core.component.VGap
import com.nevoit.material.core.interaction.rememberFlingBehavior
import com.nevoit.material.navigation.shape.clipShape
import com.nevoit.material.navigation.shape.deviceCornerRadii
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.xdnext.data.timetable.ClassBlock
import com.nevoit.xdnext.data.timetable.TimetableCalendar
import com.nevoit.xdnext.data.timetable.TimetableGrid
import com.nevoit.xdnext.data.timetable.TimetableLayout
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus

/**
 * One week: its date row, and its grid.
 *
 * Ported from `class_table_view.dart`, and the shape of it is the original's: a fixed date row over a
 * vertically scrollable stack of exactly `blockheight(61)`.
 *
 * ### How a screen height becomes a block
 *
 * [blockUnit] is handed in rather than computed here, because it is derived from the height of the
 * *pager* — the room left after the title bar, the week strip and the banner — and this composable
 * only sees its own. The formula is the original's: the room left after [TimetableSpecs.MidRowHeight],
 * divided by 48 blocks on a phone and 61 on a wide screen. On a phone the grid is therefore taller
 * than its viewport and scrolls; on a tablet it fits, and the scroll is what keeps the two honest.
 *
 * ### The layers, bottom to top
 *
 * Today's tinted column, the index column, the cards, then the current-time line. That order is the
 * original's, and it is the reason the line is drawn over the cards: at 09:05 it crosses the class that
 * is running, which is the whole point of it.
 *
 * The empty notice appears only when the week has *nothing at all* — no cards, and no current-time
 * line either. A week with no classes but today in it shows the line and no notice, which is what the
 * original did and is arguably the more useful of the two.
 */
@Composable
internal fun WeekTable(
    weekIndex: Int,
    termStart: LocalDate,
    grid: TimetableGrid,
    now: LocalDateTime,
    appearance: TimetableAppearance,
    blockUnit: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colors
    val weekStart = TimetableCalendar.weekStart(termStart, weekIndex)
    val today = now.date
    val nowMinuteOfDay = now.hour * 60 + now.minute

    val blocksByDay = remember(grid, weekIndex, weekStart) {
        (1..7).associateWith { day -> grid.blocks(weekIndex, day, weekStart) }
    }
    val todayOffset = remember(weekStart, today) { dayOffsetInWeek(weekStart, today) }

    val highlightOffset = todayOffset.takeIf { appearance.showTodayColumnHighlight }
    val indicatorOffset = todayOffset.takeIf { appearance.currentTimeIndicatorEnabled }
    val showsEmptyNotice = blocksByDay.values.all { it.isEmpty() } &&
            highlightOffset == null &&
            indicatorOffset == null

    val scrollState = rememberScrollState()
    val deviceCornerRadii = deviceCornerRadii()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val blockWidth = (maxWidth - TimetableSpecs.LeftColumnWidth) / 7
        val isPhone = maxWidth < TimetableSpecs.PhoneWidth
        val contentHeight = blockUnit * TimetableLayout.TotalBlocks.toFloat()

        Column(modifier = Modifier.fillMaxSize()) {
            TimetableDateRow(weekStart = weekStart, today = today)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(colors.cardBackground)
                    .verticalScroll(scrollState, flingBehavior = rememberFlingBehavior()),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(contentHeight)
                        .clipToBounds(),
                ) {
                    if (highlightOffset != null) {
                        TodayColumnHighlight(
                            dayOffset = highlightOffset,
                            blockWidth = blockWidth,
                            height = contentHeight
                        )
                    }

                    PeriodIndexColumn(modifier = Modifier.fillMaxHeight())

                    for ((day, blocks) in blocksByDay) {
                        for (block in blocks) {
                            ClassBlockCard(
                                block = block,
                                appearance = appearance,
                                completedHeight = completedHeightOf(
                                    block = block,
                                    dayDate = weekStart.plus(day - 1, DateTimeUnit.DAY),
                                    today = today,
                                    nowMinuteOfDay = nowMinuteOfDay,
                                    blockUnit = blockUnit,
                                ),
                                isPhone = isPhone,
                                modifier = Modifier
                                    .graphicsLayer {
                                        translationX =
                                            (TimetableSpecs.LeftColumnWidth + blockWidth * (day - 1)).toPx()
                                        translationY = (blockUnit * block.start.toFloat()).toPx()
                                    }
                                    .width(blockWidth)
                                    .height(blockUnit * block.blocks.toFloat()),
                            )
                        }
                    }

                    if (indicatorOffset != null) {
                        CurrentTimeIndicator(
                            now = now,
                            dayOffset = indicatorOffset,
                            blockUnit = blockUnit,
                            blockWidth = blockWidth,
                            appearance = appearance,
                        )
                    }
//                    if (showsEmptyNotice) {
//                        Box(
//                            modifier = Modifier
//                                .fillMaxSize()
//                                .padding(start = TimetableSpecs.LeftColumnWidth),
//                            contentAlignment = Alignment.Center,
//                        ) {
//                            Text(
//                                text = TimetableStrings.NO_CLASS,
//                                style = MaterialTheme.type.subHeadline,
//                                color = colors.contentVariant,
//                                textAlign = TextAlign.Center,
//                            )
//                        }
//                    }
                }
                VGap(
                    max(
                        deviceCornerRadii.bottomStart,
                        deviceCornerRadii.bottomEnd
                    ) + 12.dp /* Extra padding */
                )
            }
        }
    }
}

/**
 * Today's tinted column, drawn under everything else.
 *
 * Only the *drawn* column is tinted, not the date row's cell: the original's `buildDayColumnBox` was
 * positioned over the grid alone, so the header above it stays plain.
 */
@Composable
private fun TodayColumnHighlight(
    dayOffset: Int,
    blockWidth: Dp,
    height: Dp
) {
    Box(
        modifier = Modifier
            .graphicsLayer {
                translationX =
                    (TimetableSpecs.LeftColumnWidth + blockWidth * dayOffset + TimetableSpecs.BlockCardPadding).toPx()
                translationY = TimetableSpecs.BlockCardPadding.toPx()
            }
            .width(blockWidth - 2 * TimetableSpecs.BlockCardPadding)
            .height(height - 2 * TimetableSpecs.BlockCardPadding)
            .clipShape(RoundedRectangle(12.dp))
            .background(MaterialTheme.colors.secondaryContainer),
    )
}

/**
 * How much of [block] is already over, in the grid's own units.
 *
 * The date the block sits on is what decides it, not the block's own week: a class on Monday is over by
 * Tuesday whatever the clock says, and only today's classes are split by the time.
 */
private fun completedHeightOf(
    block: ClassBlock,
    dayDate: LocalDate,
    today: LocalDate,
    nowMinuteOfDay: Int,
    blockUnit: Dp,
): Dp = blockUnit * TimetableLayout.completedBlocks(
    blockStart = block.start,
    blockStop = block.stop,
    day = dayDate,
    today = today,
    nowMinuteOfDay = nowMinuteOfDay,
).toFloat()

/**
 * Where [today] falls in the week beginning [weekStart], or null when it is not in that week.
 *
 * Null rather than a negative or out-of-range number, because every caller has to decide what to do
 * about "today is not this week" and the two things that ask — the tinted column and the current-time
 * line — both draw nothing.
 */
internal fun dayOffsetInWeek(weekStart: LocalDate, today: LocalDate): Int? {
    val offset = (today.toEpochDays() - weekStart.toEpochDays()).toInt()
    return offset.takeIf { it in 0..6 }
}
