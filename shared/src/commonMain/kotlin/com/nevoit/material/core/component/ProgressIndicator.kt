package com.nevoit.material.core.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nevoit.material.theme.MaterialTheme

/**
 * A determinate bar. The track is the palette's inactive track and the fill its active one, which is
 * the same pair a switch uses — a progress bar is a track that fills.
 */
@Composable
fun LinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colors
    Box(
        modifier = modifier
            .height(4.dp)
            .clip(CircleShape)
            .background(colors.inactiveTrack),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress().coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(colors.activeTrack),
        )
    }
}

private val CircularIndicatorSize = 40.dp
private val CircularIndicatorStroke = 4.dp

/**
 * An indeterminate spinner: one rotating arc, no track.
 *
 * The arc is drawn from the *smaller* dimension and centred, because call sites size this by width
 * alone (usually to sit inside a button) and the resulting box is not square.
 */
@Composable
fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colors.primary,
    strokeWidth: Dp = CircularIndicatorStroke,
) {
    val transition = rememberInfiniteTransition()
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
    )

    Canvas(modifier = modifier.size(CircularIndicatorSize)) {
        val diameter = size.minDimension
        val stroke = strokeWidth.toPx().coerceAtMost(diameter / 2f)
        val arcSize = (diameter - stroke).coerceAtLeast(0f)
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 280f,
            useCenter = false,
            topLeft = Offset(
                x = (size.width - diameter) / 2f + stroke / 2f,
                y = (size.height - diameter) / 2f + stroke / 2f,
            ),
            size = Size(arcSize, arcSize),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}
