package com.nevoit.material.core.interaction.overscroll

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.unit.Velocity

/**
 * A band that draws on its own list only, even when that list sits inside another one.
 *
 * An [OverscrollEffect] is handed every scroll twice over: the delta, and the `performScroll` lambda
 * that can spend it. The band is drawn from the difference between them — what the list could not
 * use. Inside another list that difference is no longer the list's own leftover, because
 * `performScroll` offers everything it cannot spend to the nested-scroll parent above: the page takes
 * the scroll while a list that has run out of range does nothing, and once the page has run out too,
 * the whole difference is left over and it is the list underneath that stretches. That is the wrong
 * view moving, and it is this wrapper's reason to exist.
 *
 * This effect puts the list back in charge of its own range. [modifier] answers that leftover where it
 * is handed up — consumed, so a parent is never offered anything — and [band] is told every scroll and
 * fling minus what was answered there: the arithmetic it would have seen if it were the only scrollable
 * on the screen. So the band draws where the finger is, and the page stays put.
 *
 * Both halves belong on the same list, and both are needed — [modifier] beside [band]:
 *
 * ```
 * val band = rememberOwnRangeOverscrollEffect()
 * LazyColumn(modifier = Modifier.then(band.modifier), overscrollEffect = band)
 * ```
 *
 * A list with no range of its own is left alone rather than banded: [band] is only ever consulted while
 * a list can still scroll, and nothing here invents a band of its own. Dragging such a list moves
 * neither it nor the page.
 *
 * A list with nothing above it hands nothing up in the first place, so there this is [band] unchanged.
 */
class OwnRangeOverscrollEffect(
    private val band: OverscrollEffect,
) : OverscrollEffect {

    /** What the list last answered for: the part of a scroll or a fling it could not use. */
    private var answeredDelta = Offset.Zero
    private var answeredVelocity = Velocity.Zero

    override val isInProgress: Boolean get() = band.isInProgress

    override val node: DelegatableNode get() = band.node

    private val connection = object : NestedScrollConnection {

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            answeredDelta = available
            return available
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            answeredVelocity = available
            return available
        }
    }

    /**
     * The half that has to sit on the list itself, beside this effect.
     *
     * A nested scroll is offered upwards from the node that dispatches it, so the connection has to be
     * on the list's own modifier to be reached before the page's list. An effect's [OverscrollEffect.node]
     * is too late to be useful here: it is delegated below the list's scroll state, which is where that
     * dispatch starts from.
     */
    val modifier: Modifier = Modifier.nestedScroll(connection)

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset = band.applyToScroll(delta, source) { offered ->
        answeredDelta = Offset.Zero
        performScroll(offered) - answeredDelta
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ): Unit = band.applyToFling(velocity) { offered ->
        answeredVelocity = Velocity.Zero
        performFling(offered) - answeredVelocity
    }
}

/**
 * The app's own band, [OffsetOverscrollEffect], in the form a list inside another list wants it.
 *
 * One effect per call, as with [rememberOffsetOverscrollFactory]: a band draws for the list it belongs
 * to, so a list that keeps its scrolling to itself needs one of its own.
 */
@Composable
fun rememberOwnRangeOverscrollEffect(
    animationSpec: AnimationSpec<Float> = OffsetOverscrollEffect.DefaultAnimationSpec,
    maxFraction: Float = 0.65f,
): OwnRangeOverscrollEffect {
    val animationScope = rememberCoroutineScope()
    return remember(animationScope, animationSpec, maxFraction) {
        OwnRangeOverscrollEffect(
            OffsetOverscrollEffect(
                animationScope = animationScope,
                animationSpec = animationSpec,
                maxFraction = maxFraction,
            )
        )
    }
}
