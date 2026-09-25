package com.nevoit.xdnext.ui.timetable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.nevoit.material.core.component.Text
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.xdnext.data.timetable.IndexRow
import com.nevoit.xdnext.data.timetable.TimetableLayout
import com.nevoit.xdnext.ui.shared.rememberCardValueFontFamily

/**
 * The grid's left column: thirteen rows of period numbers and times, with the two breaks named.
 *
 * Ported from the index branch of `ClassTableView.classSubRow`. A period row prints three lines — its
 * number at 14 sp, then its start and end times at 8 sp — which is the only place in the app that says
 * when a period actually is.
 *
 * The rows are sized by **weight** rather than by an explicit height, because the thirteen of them
 * have to add up to the grid's 61 blocks exactly: 5+5+5+5 for the morning, 3 for 午休, and so on down
 * the column. Laying them out by height would accumulate rounding and leave a seam at the bottom.
 *
 * The three lines are three texts rather than one with spans, and each carries its own line height.
 * Flutter's `height` is a *multiplier* of the span's own font size — the original's 8 sp times are
 * 11.4 logical pixels apart, not 20 — while Compose's is an absolute value that a mixed-size paragraph
 * can only set once. Three sibling texts are the honest way to say "14 sp at 20, then 8 sp at 11.5"
 * inside a row that is only five blocks tall.
 */
@Composable
internal fun PeriodIndexColumn(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colors
    Column(
        modifier = modifier
            .width(TimetableSpecs.LeftColumnWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (row in 0 until TimetableLayout.IndexRows) {
            Box(
                modifier = Modifier
                    .weight(TimetableLayout.indexRowBlocks(row).toFloat()),
                contentAlignment = Alignment.Center,
            ) {
                when (val indexRow = TimetableLayout.indexRow(row)) {
                    is IndexRow.Break -> Text(
                        text = indexRow.label,
                        style = MaterialTheme.type.footnote,
                        color = colors.contentVariant,
                        textAlign = TextAlign.Center,
                    )

                    is IndexRow.Period -> PeriodLabel(indexRow.number)
                }
            }
        }
    }
}

@Composable
private fun PeriodLabel(period: Int) {
    val colors = MaterialTheme.colors
    val font = rememberCardValueFontFamily()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = period.toString(),
            style = MaterialTheme.type.footnote.copy(fontFamily = font),
            color = colors.content
        )
        Text(
            text = TimetableLayout.PeriodTimes[(period - 1) * 2],
            fontSize = 8.sp,
            lineHeight = 12.sp,
            fontFamily = font,
            color = colors.contentVariant
        )
        Text(
            text = TimetableLayout.PeriodTimes[(period - 1) * 2 + 1],
            fontSize = 8.sp,
            lineHeight = 12.sp,
            fontFamily = font,
            color = colors.contentVariant
        )
    }
}
