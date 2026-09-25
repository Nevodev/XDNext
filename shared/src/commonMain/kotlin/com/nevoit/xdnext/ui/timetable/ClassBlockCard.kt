package com.nevoit.xdnext.ui.timetable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.shapes.RoundedRectangle
import com.nevoit.material.core.component.Text
import com.nevoit.xdnext.data.timetable.ClassBlock
import com.nevoit.xdnext.ui.home.rememberCardValueFontFamily

/**
 * One rectangle of the grid: a class, or several that clash, drawn as one card.
 *
 * Ported from `class_card.dart`. The card is a small stack of three layers, drawn in this order for a
 * reason:
 *
 * 1. **the fill**, split horizontally where the class has got to. The part already over is painted in
 *    the completed colour and the rest in the active one, so a class in progress reads as a bar that
 *    fills down the card as the hour passes. With the default settings both halves are the same colour
 *    and the split is invisible — see [TimetableAppearance].
 * 2. **the text**: the name at the top-left, then `@教室`, then a count if the card holds more than one
 *    arrangement.
 * 3. **a corner triangle**, when it does hold more than one — drawn in the text's own colour, so it
 *    dims with the text on a finished class instead of standing out on it.
 *
 * The colour comes from the generated palette — see [classSwatchFor], which is the one place a course's
 * colour is looked up — and never from a table in the source: one course is one [ClassSwatch], its fill is
 * [ClassSwatch.primaryContainer] and everything drawn on that fill is [ClassSwatch.onPrimaryContainer],
 * which is a Material container pair and therefore readable in both themes. What the appearance
 * settings change is only how opaque those two are; the original's three hand-picked tones and the HSL
 * factors that adjusted them are gone.
 *
 * The text's opacity follows whether the class is *entirely* over, not how much of it is: a class that
 * is halfway through keeps full-strength text, and the completed fill behind it is what shows the
 * difference. That is the original's rule, and it is why the split and the text can disagree without
 * looking wrong.
 *
 * The original wrapped the card in a `TextButton` that opened a detail sheet listing every arrangement
 * in it. That sheet is not built yet, so the card is not clickable — a tap target that opens nothing
 * is worse than a still card. [ClassBlock.entries] is what the sheet will read.
 */
@Composable
internal fun ClassBlockCard(
    block: ClassBlock,
    appearance: TimetableAppearance,
    completedHeight: Dp,
    isPhone: Boolean,
    modifier: Modifier = Modifier,
) {
    val swatch = classSwatchFor(block.paletteIndex)

    Box(modifier.padding(TimetableSpecs.BlockCardPadding)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedRectangle(8.dp)),
        ) {
            val splitHeight = completedHeight.coerceIn(0.dp, maxHeight)
            val isCompleted = splitHeight >= maxHeight - 0.5.dp
            val completedAlpha = appearance.cardAlpha(isCompleted = true)
            val contentColor = swatch.onPrimaryContainer.copy(
                alpha = appearance.cardAlpha(isCompleted),
            )

            if (splitHeight > 0.dp) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(splitHeight)
                        .background(swatch.primaryContainer.copy(alpha = completedAlpha)),
                )
            }
            if (splitHeight < maxHeight) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(maxHeight - splitHeight)
                        .background(swatch.primaryContainer),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 2.dp,
                        vertical = 4.dp,
                    )
            ) {
                Text(
                    text = block.name,
                    color = contentColor,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                )
                Text(
                    text = block.place ?: TimetableStrings.UNKNOWN_CLASSROOM,
                    color = contentColor,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    fontFamily = rememberCardValueFontFamily()
                )
                if (block.isMerged) {
                    Text(
                        text = TimetableStrings.remainsHint(block.entryCount - 1),
                        color = contentColor,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            }

            if (block.isMerged) {
                Canvas(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(TimetableSpecs.MergedMarkerSize),
                ) {
                    val triangle = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width, size.height)
                        close()
                    }
                    drawPath(triangle, color = contentColor)
                }
            }
        }
    }
}
