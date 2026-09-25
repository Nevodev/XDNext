package com.nevoit.material.core.interaction.overscroll

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope

@Composable
fun rememberRubberBandState(): RubberBandState = remember { RubberBandState() }

@Composable
fun rememberOffsetOverscrollFactory(
    animationSpec: AnimationSpec<Float> = OffsetOverscrollEffect.DefaultAnimationSpec,
    maxFraction: Float = 0.65f,
    rubberBandState: RubberBandState? = null,
    onRelease: ((Offset) -> Unit)? = null,
): OverscrollFactory {
    val animationScope = rememberCoroutineScope()
    return remember(animationScope, animationSpec, maxFraction, rubberBandState, onRelease) {
        OffsetOverscrollFactory(
            animationScope = animationScope,
            animationSpec = animationSpec,
            maxFraction = maxFraction,
            rubberBandState = rubberBandState,
            onRelease = onRelease
        )
    }
}

internal data class OffsetOverscrollFactory(
    private val animationScope: CoroutineScope,
    private val animationSpec: AnimationSpec<Float> = OffsetOverscrollEffect.DefaultAnimationSpec,
    private val maxFraction: Float = 0.65f,
    private val rubberBandState: RubberBandState? = null,
    private val onRelease: ((Offset) -> Unit)? = null,
) : OverscrollFactory {

    override fun createOverscrollEffect(): OverscrollEffect {
        return OffsetOverscrollEffect(
            rubberBandState = rubberBandState,
            onRelease = onRelease,
            animationScope = animationScope,
            animationSpec = animationSpec,
            maxFraction = maxFraction
        )
    }
}
