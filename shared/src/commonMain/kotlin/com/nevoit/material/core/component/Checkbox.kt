package com.nevoit.material.core.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nevoit.material.theme.MaterialTheme

/**
 * A checkbox: a filled-and-ticked box when checked, an outline when not.
 *
 * The tick is drawn with two lines rather than an icon, so this component has no dependency on the
 * icon font being provided — a checkbox should not need the whole icon stack to render.
 */
@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colors
    val shape = RoundedCornerShape(4.dp)
    val boxColor = when {
        !enabled -> colors.content.copy(alpha = 0.12f)
        checked -> colors.primary
        else -> Color.Transparent
    }
    val markColor = if (enabled) colors.onPrimary else colors.content.copy(alpha = 0.38f)

    Box(
        modifier = modifier
            .size(20.dp)
            .clip(shape)
            .background(boxColor)
            .border(2.dp, if (checked) boxColor else colors.inactiveThumb, shape)
            .toggleable(
                value = checked,
                enabled = enabled && onCheckedChange != null,
                role = Role.Checkbox,
                onValueChange = { onCheckedChange?.invoke(it) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Canvas(modifier = Modifier.size(12.dp)) {
                val stroke = size.minDimension * 0.16f
                drawLine(
                    color = markColor,
                    start = Offset(0f, size.height * 0.5f),
                    end = Offset(size.width * 0.33f, size.height * 0.85f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = markColor,
                    start = Offset(size.width * 0.33f, size.height * 0.85f),
                    end = Offset(size.width, size.height * 0.15f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
