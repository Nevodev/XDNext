package com.nevoit.material.core.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.material.theme.local.LocalContentColor
import com.nevoit.material.theme.local.ProvideTextStyle
import com.nevoit.material.theme.tokens.Springs
import kotlinx.coroutines.launch

val DefaultNavigationBarHeight = 80.dp
private val IndicatorWidth = 64.dp
private val IndicatorHeight = 32.dp
private const val IndicatorDurationMillis = 200

@Composable
fun NavigationBar(
    modifier: Modifier = Modifier,
    height: Dp = DefaultNavigationBarHeight,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(height),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun RowScope.NavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val shape = Capsule()

    val sizeProgress = remember { Animatable(if (selected) 1f else 0f) }
    val fadeProgress = remember { Animatable(if (selected) 1f else 0f) }
    val spec = Springs.smooth<Float>(durationMillis = IndicatorDurationMillis)

    LaunchedEffect(selected) {
        if (selected) {
            launch { sizeProgress.animateTo(1f, spec) }
            launch { fadeProgress.animateTo(1f, spec) }
        } else {
            fadeProgress.animateTo(0f, spec)
            sizeProgress.snapTo(0f)
        }
    }

    val iconColor by animateColorAsState(
        targetValue = if (selected) {
            colors.onNavigationBarIndicator
        } else {
            colors.navigationBarInactiveIcon
        },
        animationSpec = Springs.smooth(durationMillis = IndicatorDurationMillis),
        label = "navigationBarIconColor",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) {
            colors.navigationBarActiveLabel
        } else {
            colors.navigationBarInactiveLabel
        },
        animationSpec = Springs.smooth(durationMillis = IndicatorDurationMillis),
        label = "navigationBarLabelColor",
    )

    Column(
        modifier = modifier
            .weight(1f)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(IndicatorWidth, IndicatorHeight)
                .drawBehind {
                    val pillWidth = IndicatorWidth.toPx() * sizeProgress.value
                    val alpha = fadeProgress.value
                    if (pillWidth <= 0f || alpha <= 0f) return@drawBehind

                    val outline = shape.createOutline(
                        size = Size(pillWidth, size.height),
                        layoutDirection = layoutDirection,
                        density = this,
                    )
                    translate(left = (size.width - pillWidth) / 2f) {
                        drawOutline(
                            outline = outline,
                            color = colors.navigationBarIndicator,
                            alpha = alpha,
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides iconColor, content = icon)
        }

        CompositionLocalProvider(LocalContentColor provides labelColor) {
            ProvideTextStyle(MaterialTheme.type.footnote, label)
        }
    }
}
