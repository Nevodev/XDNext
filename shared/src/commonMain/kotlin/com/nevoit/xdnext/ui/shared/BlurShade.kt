package com.nevoit.xdnext.ui.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur

@Composable
fun BlurShade(
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop,
    visible: Boolean = true
) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f)

    Box(
        modifier = Modifier
            .graphicsLayer {
                this.alpha = alpha
            }.drawPlainBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = {
                    padding = 32.dp.toPx()
                    blur(48.dp.toPx())
                }).then(modifier),
    )
}