package com.nevoit.material.core.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.material.theme.local.LocalContentColor
import com.nevoit.material.theme.local.ProvideTextStyle

/**
 * A selectable chip.
 *
 * Its colours are the palette's segmented-control roles — the selected chip *is* a segment of a
 * segmented control, so it uses the same three colours rather than inventing a second set for the
 * same idea.
 */
@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colors
    val shape = MaterialTheme.specs.buttonShape
    val contentColor = when {
        !enabled -> colors.content.copy(alpha = 0.38f)
        selected -> colors.onSegmentedControlBackground
        else -> colors.content
    }

    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.segmentedControlBackground else Color.Transparent)
            .then(
                if (selected) Modifier
                else Modifier.border(1.dp, colors.inactiveThumb, shape)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = 32.dp)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(MaterialTheme.type.subHeadlineEmphasized) {
                label()
            }
        }
    }
}
