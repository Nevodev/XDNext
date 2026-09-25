package com.nevoit.xdnext.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.shapes.Capsule
import com.nevoit.material.core.component.Text
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.xdnext.data.timetable.TimetableLayout
import com.nevoit.xdnext.ui.shared.rememberCardValueFontFamily
import kotlinx.datetime.LocalDateTime
import kotlin.math.max

/**
 * The line that says where the day has got to.
 *
 * Ported from `current_time_indicator.dart`. It is two things in one bar: a `HH:mm` clock in the index
 * column, and a line that runs from there to the end of today's column. Only the *today* segment is at
 * full strength — the connector across the days before it is a third as opaque, so the eye is drawn to
 * where the day actually is rather than to the whole row.
 *
 * The vertical position is continuous. Above the first period it sits at the top of the grid, below the
 * last one at the bottom, and in between it moves *through* the periods and through the breaks, at a
 * rate taken from the break's own height rather than from the clock alone — so the long lunch break
 * carries it three blocks and the five minutes between two periods barely move it. See
 * [TimetableLayout.blockOf].
 *
 * The label is centred on the line rather than sitting above it, which is why the box this draws starts
 * half a label's height above the line and is only as tall as the lower half needs.
 */
@Composable
internal fun CurrentTimeIndicator(
    now: LocalDateTime,
    dayOffset: Int,
    blockUnit: Dp,
    blockWidth: Dp,
    appearance: TimetableAppearance,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colors

    val labelHeight = 14.dp
    val lineThickness = 2.dp

    val lineTop = blockUnit * TimetableLayout.blockOf(now.hour, now.minute).toFloat()
    val indicatorTop = lineTop - labelHeight
    val lineTopOffset = labelHeight - lineThickness / 2f
    val labelTop = labelHeight - labelHeight / 2f
    val labelBottom = labelTop + labelHeight
    val lineBottom = lineTopOffset + lineThickness
    val indicatorHeight = if (appearance.showTimeLabel) {
        max(labelBottom.value, lineBottom.value).dp
    } else {
        lineBottom
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                translationY = indicatorTop.toPx()
            }
            .width(TimetableSpecs.LeftColumnWidth + blockWidth * (dayOffset + 1))
            .height(indicatorHeight),
    ) {
        if (appearance.showTimeLabel) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationY = labelTop.toPx()
                    }
                    .width(TimetableSpecs.LeftColumnWidth)
                    .height(labelHeight)
                    .clip(Capsule())
                    .background(colors.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = clockText(now),
                    color = colors.onSecondaryContainer,
                    fontSize = 8.sp,
                    lineHeight = 8.sp,
                    fontFamily = rememberCardValueFontFamily()
                )
            }
        }

        if (dayOffset > 0) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = TimetableSpecs.LeftColumnWidth.toPx()
                        translationY = lineTopOffset.toPx()
                    }
                    .width(blockWidth * dayOffset + TimetableSpecs.BlockCardPadding)
                    .height(lineThickness)
                    .background(colors.primary.copy(.5f)),
            )
        }

        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX =
                        (TimetableSpecs.LeftColumnWidth + blockWidth * dayOffset + TimetableSpecs.BlockCardPadding).toPx()
                    translationY = lineTopOffset.toPx()
                }
                .width(blockWidth - TimetableSpecs.BlockCardPadding * 2)
                .height(lineThickness)
                .background(colors.primary),
        )
    }
}

/** `09:05`, zero-padded, as the original formatted it. */
private fun clockText(now: LocalDateTime): String =
    "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}"
