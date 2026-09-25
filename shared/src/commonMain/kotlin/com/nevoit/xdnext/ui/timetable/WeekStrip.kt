package com.nevoit.xdnext.ui.timetable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle
import com.nevoit.material.core.component.Surface
import com.nevoit.material.core.component.Text
import com.nevoit.material.core.component.VGap
import com.nevoit.material.core.interaction.rememberSnapFlingBehavior
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
 * The week selector: a horizontal strip of week buttons, one per teaching week.
 *
 * Ported from `ContentClassTablePage._topView()` and `week_choice_view.dart`. Each button is 74 dp wide
 * with 2 dp either side, the selected one is tinted, and — on a screen taller than 500 dp — the button
 * also carries a 5×5 preview of that week: five rows of slots down, five days across.
 *
 * ### Scrolling browses; tapping selects
 *
 * The strip scrolls, and scrolling it **never** changes which week is selected — the tint follows the
 * selection, not the strip's position. Only a tap moves the selection, and the strip then slides to
 * bring that week to the left edge. That is a deliberate departure from the original, whose strip was a
 * `PageView` that wrote the chosen week back whenever it settled on a page: reaching week 15 meant
 * dragging fifteen pages past and *changing the grid* fifteen times on the way. Here the strip is a way
 * to reach a week, and picking one is a separate act.
 *
 * ### Why it snaps, and with what
 *
 * A freely scrolling strip leaves a week button cut in half at the edge, which reads as "this is the
 * week" when it is not. The strip therefore uses the project's own velocity-preserving snap fling —
 * `rememberSnapFlingBehavior` — which lets a drag carry on under its own momentum and then settles the
 * nearest button onto the leading edge. [SnapPosition.Start] rather than the helper's default centre,
 * because the original's `padEnds: false` put week one flush against the left edge and a centre snap
 * cannot hold that.
 *
 * [LazyListState] is owned by the caller, as the original owned its `rowControl` outside this view: the
 * grid can still be swiped, and the strip has to be slid to follow it.
 */
@Composable
internal fun WeekStrip(
    listState: LazyListState,
    semesterLength: Int,
    chosenWeek: Int,
    currentWeek: Int,
    termStart: LocalDate,
    grid: TimetableGrid,
    now: LocalDateTime,
    showOverview: Boolean,
    onWeekSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (showOverview) 88.dp else 32.dp)
    ) {
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(
                lazyListState = listState,
                snapPosition = SnapPosition.Start,
                unconsumedDeltaThreshold = 5f
            ),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(count = semesterLength, key = { it }) { page ->
                WeekChoiceCard(
                    weekIndex = page,
                    selected = page == chosenWeek,
                    currentWeek = currentWeek,
                    showOverview = showOverview,
                    grid = grid,
                    termStart = termStart,
                    now = now,
                    onClick = { onWeekSelected(page) },
                )
            }
        }
    }
}

@Composable
private fun WeekChoiceCard(
    weekIndex: Int,
    selected: Boolean,
    currentWeek: Int,
    showOverview: Boolean,
    grid: TimetableGrid,
    termStart: LocalDate,
    now: LocalDateTime,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colors
    Surface(
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedRectangle(12.dp))
            .clickable(onClick = onClick),
        color = if (selected) colors.primaryContainer else colors.cardBackground,
        contentColor = if (selected) colors.onPrimaryContainer else colors.content,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp).padding(bottom = 4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = TimetableStrings.weekTitle(weekIndex + 1),
                style = MaterialTheme.type.footnote.copy(
                    fontWeight = if (weekIndex == currentWeek) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Medium
                    },
                ),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            if (showOverview) {
                VGap(4.dp)
                WeekOverview(
                    weekStart = TimetableCalendar.weekStart(termStart, weekIndex),
                    today = now.date,
                    nowMinuteOfDay = now.hour * 60 + now.minute,
                    grid = grid,
                    weekIndex = weekIndex,
                    modifier = Modifier.size(40.dp),
                    color = if (selected) colors.onPrimaryContainer else colors.primary
                )
            }
        }
    }
}

@Composable
private fun WeekOverview(
    weekStart: LocalDate,
    today: LocalDate,
    nowMinuteOfDay: Int,
    grid: TimetableGrid,
    weekIndex: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val currentBlock =
        remember(nowMinuteOfDay) { TimetableLayout.blockOfMinuteOfDay(nowMinuteOfDay) }
    val blocksByDay = remember(grid, weekIndex, weekStart) {
        (1..OverviewDays).associateWith { day -> grid.blocks(weekIndex, day, weekStart) }
    }
    val strengths = remember(blocksByDay, weekStart, today, currentBlock) {
        overviewStrengths(blocksByDay, weekStart, today, currentBlock)
    }

    Canvas(modifier = modifier) {
        val gap = OverviewGap.toPx()
        val dotSize = ((size.width - gap * (OverviewDays - 1)) / OverviewDays).coerceAtLeast(0f)

        for (row in SlotRanges.indices) {
            val top = row * (dotSize + gap)
            for (column in 0 until OverviewDays) {
                drawOval(
                    color = color,
                    topLeft = Offset(column * (dotSize + gap), top),
                    size = Size(dotSize, dotSize),
                    alpha = strengths[row][column],
                )
            }
        }
    }
}

/** The gap a dot keeps from its neighbours: between two columns, and between two rows. */
private val OverviewGap = 2.5f.dp

/**
 * The strength of all twenty-five cells, indexed by slot row and then by day column.
 *
 * A cell is occupied as soon as anything reaches into its slot, and counts as over only while
 * *everything* that reaches into it is over — so the scan for a cell stops at the first unfinished
 * class and leaves it solid, which is what makes a finished class clashing with a running one read as
 * running rather than as done.
 */
private fun overviewStrengths(
    blocksByDay: Map<Int, List<ClassBlock>>,
    weekStart: LocalDate,
    today: LocalDate,
    currentBlock: Double,
): Array<FloatArray> = Array(SlotRanges.size) { row ->
    val slot = SlotRanges[row]
    FloatArray(OverviewDays) { column ->
        val day = column + 1
        var occupied = false
        var allCompleted = true

        for (block in blocksByDay[day].orEmpty()) {
            if (!occupiesSlot(block, slot)) continue
            occupied = true
            val completed = slotCompleted(
                dayDate = weekStart.plus(day - 1, DateTimeUnit.DAY),
                today = today,
                slotStop = slot.stop,
                currentBlock = currentBlock,
            )
            allCompleted = completed
            if (!completed) break
        }

        dotAlpha(occupied, allCompleted)
    }
}

/** The five days the preview covers. */
private const val OverviewDays = 5

/**
 * The vertical span the preview's five rows stand for, as grid blocks.
 *
 * The original's own five pairs, kept verbatim including the odd last one: the fifth row starts at
 * block 46 rather than at 43, because 43 to 46 is the supper break and the preview has no room to
 * distinguish it from the period after it.
 */
private data class Slot(val start: Int, val stop: Int)

private val SlotRanges = listOf(
    Slot(0, 10),
    Slot(10, 20),
    Slot(20, 33),
    Slot(33, 43),
    Slot(46, 49),
)

/** Whether [block] has any part of itself inside the preview cell [slot]. */
private fun occupiesSlot(block: ClassBlock, slot: Slot): Boolean {
    val slotStart = slot.start.toDouble()
    val slotStop = slot.stop.toDouble()
    return block.stop != slotStart &&
            block.start != slotStop &&
            ((slotStart < block.stop && block.start < slotStop) ||
                    (slotStop > block.start && block.stop > slotStart))
}

/** Whether everything in the cell is over by [currentBlock]. */
private fun slotCompleted(
    dayDate: LocalDate,
    today: LocalDate,
    slotStop: Int,
    currentBlock: Double,
): Boolean {
    if (dayDate < today) return true
    if (dayDate > today) return false
    return slotStop.toDouble() <= currentBlock
}

/** The original's three strengths: vacant, finished, running. */
private fun dotAlpha(occupied: Boolean, allCompleted: Boolean): Float = when {
    !occupied -> 0.25f
    allCompleted -> 0.45f
    else -> 1f
}
