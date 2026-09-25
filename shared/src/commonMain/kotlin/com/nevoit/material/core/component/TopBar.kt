package com.nevoit.material.core.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.material.theme.tokens.Springs
import kotlinx.coroutines.launch

private enum class TopBarSlot {
    Leading, Title, Trailing
}

private val LeadingModifier = Modifier.layoutId(TopBarSlot.Leading)
private val TitleModifier = Modifier.layoutId(TopBarSlot.Title)
private val TrailingModifier = Modifier.layoutId(TopBarSlot.Trailing)

private val TopBarHeight = 72.dp

private val Gap = 12.dp

@Composable
fun TopBar(
    title: String?,
    titleVisible: () -> Boolean = { true },
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val showTitle = title != null && titleVisible()

    val textAlpha = remember { Animatable(if (showTitle) 1f else 0f) }
    val scale = remember { Animatable(if (showTitle) 1f else 0.85f) }

    LaunchedEffect(showTitle) {
        launch {
            textAlpha.animateTo(
                targetValue = if (showTitle) 1f else 0f,
                animationSpec = tween(if (showTitle) 300 else 200)
            )
        }
        launch {
            scale.animateTo(
                targetValue = if (showTitle) 1f else 0.85f,
                animationSpec = Springs.smooth(if (showTitle) 300 else 400)
            )
        }
    }

    Layout(
        modifier = modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(TopBarHeight),
        content = {
            leading?.let { Box(modifier = LeadingModifier) { it() } }
            title?.let {
                Text(
                    modifier = TitleModifier,
                    text = title,
                    style = MaterialTheme.type.title4Emphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing?.let { Box(modifier = TrailingModifier) { it() } }
        }
    ) { measurables, constraints ->
        val leadingMeasurable = measurables.firstOrNull { it.layoutId == TopBarSlot.Leading }
        val titleMeasurable = measurables.firstOrNull { it.layoutId == TopBarSlot.Title }
        val trailingMeasurable = measurables.firstOrNull { it.layoutId == TopBarSlot.Trailing }

        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = Gap.roundToPx()
        val bounded = constraints.hasBoundedWidth

        val leadingPlaceable = leadingMeasurable?.measure(loose)
        val leadingWidth = leadingPlaceable?.width ?: 0

        val trailingPlaceable = trailingMeasurable?.measure(
            if (bounded) {
                loose.copy(maxWidth = (constraints.maxWidth - leadingWidth).coerceAtLeast(0))
            } else {
                loose
            }
        )
        val trailingWidth = trailingPlaceable?.width ?: 0

        val titleMaxWidth = if (bounded) {
            val reserved = leadingWidth + trailingWidth +
                    (if (leadingPlaceable != null) gap else 0) +
                    (if (trailingPlaceable != null) gap else 0)
            (constraints.maxWidth - reserved).coerceAtLeast(0)
        } else {
            Constraints.Infinity
        }
        val titlePlaceable = titleMeasurable?.measure(loose.copy(maxWidth = titleMaxWidth))

        val width = if (bounded) {
            constraints.maxWidth
        } else {
            leadingWidth + (titlePlaceable?.width ?: 0) + trailingWidth + gap * 2
        }
        val height = maxOf(
            leadingPlaceable?.height ?: 0,
            titlePlaceable?.height ?: 0,
            trailingPlaceable?.height ?: 0,
        ).coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(width, height) {
            leadingPlaceable?.placeRelative(
                x = 0,
                y = Alignment.CenterVertically.align(leadingPlaceable.height, height),
            )

            if (titlePlaceable != null) {
                val titleX = leadingWidth + if (leadingPlaceable != null) gap else 0
                val titleY = Alignment.CenterVertically.align(titlePlaceable.height, height)
                titlePlaceable.placeRelativeWithLayer(x = titleX, y = titleY) {
                    val currentAlpha = textAlpha.value
                    this.alpha = currentAlpha
                    this.scaleX = scale.value
                    this.scaleY = scale.value
                }
            }

            trailingPlaceable?.placeRelative(
                x = width - trailingWidth,
                y = Alignment.CenterVertically.align(trailingPlaceable.height, height),
            )
        }
    }
}