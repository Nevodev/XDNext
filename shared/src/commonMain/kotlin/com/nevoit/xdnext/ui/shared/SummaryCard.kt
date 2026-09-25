package com.nevoit.xdnext.ui.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.nevoit.material.core.component.Text
import com.nevoit.material.theme.MaterialShapes
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.material.theme.toShape
import com.nevoit.xdnext.ui.home.Card
import com.nevoit.xdnext.ui.home.IconBox

/**
 * The card a page leads with: one number at reading size, its unit, a line under it, and a badge.
 *
 * Lifted out of the electricity page, where it was the reading and nothing else. What a *page* does
 * with it stays with the page — the value, the unit, the caption and the symbol all come from the
 * caller, which is why the loading and failure wording ("正在查询", "请检查网络后重试") is next to the
 * reading it describes rather than in here.
 *
 * [iconBackground] and [iconContentColor] are parameters rather than the primary container baked in:
 * the same badge is where a page would say "this number wants attention", and a caller that has a
 * reason to paint it in the error colours should not have to fork the card to do it.
 *
 * @param value the number as it is printed, already formatted — this card does not know what it counts.
 * @param caption the line under it, usually a date or an explanation of where the value came from.
 * @param icon the symbol drawn in the badge.
 * @param unit the unit, set inside the same text as [value] so the two share a baseline and a font
 *   fallback; null for a value that needs none.
 */
@Composable
fun SummaryCard(
    value: String,
    caption: String,
    icon: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    iconShape: Shape = MaterialShapes.VerySunny.toShape(),
    iconBackground: Color = MaterialTheme.colors.primaryContainer,
    iconContentColor: Color = MaterialTheme.colors.onPrimaryContainer,
) {
    Card(modifier = modifier.height(Height)) {
        Box(modifier = Modifier.fillMaxSize().padding(Padding)) {
            IconBox(
                icon = icon,
                modifier = Modifier.align(Alignment.CenterEnd),
                shape = iconShape,
                background = iconBackground,
                contentColor = iconContentColor,
            )
            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                Text(
                    text = rememberValueWithUnit(
                        value = value,
                        unit = unit,
                        style = MaterialTheme.type.title1,
                    )
                )
                Text(
                    text = caption,
                    style = MaterialTheme.type.footnote,
                    color = MaterialTheme.colors.contentVariant,
                )
            }
        }
    }
}

/**
 * The card's size, fixed so that two pages leading with a different number — or the same page before
 * and after its first query — do not jump by a line as the text under the number changes.
 */
private val Height = 128.dp

private val Padding = 16.dp
