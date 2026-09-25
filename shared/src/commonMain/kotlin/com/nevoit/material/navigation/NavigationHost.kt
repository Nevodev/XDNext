package com.nevoit.material.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.nevoit.material.theme.tokens.Springs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel as androidxViewModel

private class PageTransition(
    initialProgress: Float,
    initialUnderlayProgress: Float,
    val parentEntryId: Long?,
    parentLifecycle: Lifecycle,
) {
    val progress = Animatable(initialProgress)
    val underlayProgress = Animatable(initialUnderlayProgress)
    var ready by mutableStateOf(initialProgress == 1f)
    var targetProgress by mutableFloatStateOf(1f)
    var isPageTransitioning by mutableStateOf(initialProgress != 1f)
    val lifecycleOwner = NavigationPageLifecycleOwner(parentLifecycle)
}

private class NavigationPageLifecycleOwner(
    private val parentLifecycle: Lifecycle,
) : LifecycleOwner, LifecycleEventObserver {
    private val registry = LifecycleRegistry(this)
    private var active = true

    override val lifecycle: Lifecycle = registry

    init {
        parentLifecycle.addObserver(this)
        syncState()
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        syncState()
    }

    fun deactivate() {
        active = false
        syncState()
    }

    fun activate() {
        active = true
        syncState()
    }

    fun destroy() {
        parentLifecycle.removeObserver(this)
        registry.currentState = Lifecycle.State.DESTROYED
    }

    private fun syncState() {
        registry.currentState = when {
            parentLifecycle.currentState == Lifecycle.State.DESTROYED -> {
                Lifecycle.State.DESTROYED
            }

            !parentLifecycle.currentState.isAtLeast(Lifecycle.State.CREATED) -> {
                Lifecycle.State.INITIALIZED
            }

            active && parentLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) -> {
                Lifecycle.State.STARTED
            }

            else -> Lifecycle.State.CREATED
        }
    }
}

/**
 * Renders every entry in [navigator] and coordinates page transitions, system back handling,
 * saveable state, lifecycle state, and per-page ViewModel stores.
 *
 * The receiver content is invoked once for each visible page. Use [NavigationScope.destination] to
 * render the route and [NavigationScope.navigate] or [NavigationScope.back] from page actions. When
 * [pageViewModelStores] is omitted, the host creates one ViewModel-backed store manager for the
 * current ViewModel owner.
 *
 * The slide distance is the host's own width. [windowWidthPx] overrides it — two hosts side by side in
 * a two-pane layout each measure their own pane, which is what makes that layout work — and
 * [underlayTranslation], [scrimColor], [scrimAlpha] and [transitionModifier] are optional visual
 * policy hooks; they do not participate in back-stack or lifecycle decisions.
 *
 * Ported from `Bilineo/.../core/navigation`, with two changes for Compose Multiplatform: the window
 * width is measured by the host itself rather than read from `LocalWindowInfo`, and system back goes
 * through [PlatformBackHandler] instead of `androidx.activity.compose` directly — see its KDoc for why
 * that one is hand-rolled.
 */
@Composable
fun <D : Any> NavigationHost(
    modifier: Modifier = Modifier,
    navigator: Navigator<D>,
    pageViewModelStores: PageViewModelStores? = null,
    windowWidthPx: Int? = null,
    animationSpec: AnimationSpec<Float>? = null,
    underlayTranslation: (windowWidthPx: Int, progress: Float) -> Float = { windowWidthPx, progress ->
        -(windowWidthPx / 3f) * progress
    },
    scrimColor: Color = Color.Black,
    scrimAlpha: Float = 0.6f,
    transitionModifier: (Modifier, isTransitioning: Boolean) -> Modifier = { page, _ -> page },
    content: @Composable NavigationScope<D>.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // An unbounded parent (a horizontal scroller, say) has no meaningful slide distance; zero
        // makes the page appear in place instead of sliding from infinity.
        val hostWidthPx = windowWidthPx
            ?: constraints.maxWidth.takeIf { it in 1 until Int.MAX_VALUE }
            ?: 0
        val resolvedAnimationSpec = animationSpec
            ?: Springs.smooth(350, 0.0, if (hostWidthPx > 0) 1f / hostWidthPx else 1f)

        val stores = pageViewModelStores ?: androidxViewModel<PageViewModelStores>()
        val parentLifecycle = LocalLifecycleOwner.current.lifecycle
        val initialBackStack = remember(navigator) { navigator.backStack }
        // A plain list in a state holder, not `mutableStateListOf`: the visible set is only ever
        // replaced wholesale, and `SnapshotStateList` is `android.os.Parcelable` in the Android source
        // set — a supertype a common source set cannot resolve, which is what makes an IDE report
        // "Cannot access 'android.os.Parcelable' which is a supertype of 'SnapshotStateList'".
        var visibleEntries by remember(navigator) { mutableStateOf(initialBackStack) }
        val transitions = remember(navigator, parentLifecycle) {
            mutableMapOf<Long, PageTransition>().apply {
                initialBackStack.forEachIndexed { index, entry ->
                    this[entry.id] = PageTransition(
                        initialProgress = 1f,
                        initialUnderlayProgress = 1f,
                        parentEntryId = initialBackStack.getOrNull(index - 1)?.id,
                        parentLifecycle = parentLifecycle,
                    )
                }
            }
        }
        val pageStateHolder = rememberSaveableStateHolder()

        DisposableEffect(transitions) {
            onDispose {
                transitions.values.forEach { transition ->
                    transition.lifecycleOwner.destroy()
                }
            }
        }

        fun release(entry: PageEntry<D>) {
            visibleEntries -= entry
            transitions.remove(entry.id)?.lifecycleOwner?.destroy()
            pageStateHolder.removeState(entry.id)
            stores.remove(entry.id)
        }

        fun open(destination: D) {
            val exitingEntry = visibleEntries.lastOrNull { entry ->
                entry.destination == destination && transitions[entry.id]?.targetProgress == 0f
            }
            if (exitingEntry != null) {
                val transition = transitions.getValue(exitingEntry.id)
                navigator.reopen(exitingEntry)
                transition.lifecycleOwner.activate()
                transition.targetProgress = 1f
                transition.isPageTransitioning = true
                // Move the entry back to the top of the draw order.
                visibleEntries = visibleEntries - exitingEntry + exitingEntry
                return
            }
            if (navigator.current.destination == destination) return

            val parentEntry = navigator.current
            val interruptedSibling = visibleEntries.asReversed().firstOrNull { candidate ->
                transitions.getValue(candidate.id).parentEntryId == parentEntry.id
            }
            val initialUnderlayProgress = interruptedSibling?.let { sibling ->
                transitions.getValue(sibling.id).underlayProgress.value
            } ?: 0f
            val entry = navigator.open(destination)
            transitions[entry.id] = PageTransition(
                initialProgress = 0f,
                initialUnderlayProgress = initialUnderlayProgress,
                parentEntryId = parentEntry.id,
                parentLifecycle = parentLifecycle,
            )
            visibleEntries += entry
        }

        fun closeTop() {
            val entry = navigator.current
            val transition = transitions[entry.id] ?: return
            if (navigator.closeTop(expectedEntryId = entry.id) == null) return

            transition.lifecycleOwner.deactivate()
            transition.targetProgress = 0f
            transition.isPageTransitioning = true
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val entryTransitions = visibleEntries.map { entry -> transitions.getValue(entry.id) }
            val underlayDrivers = mutableMapOf<Long, PageTransition>()
            visibleEntries.asReversed().forEach { candidate ->
                val candidateTransition = transitions.getValue(candidate.id)
                val parentEntryId = candidateTransition.parentEntryId ?: return@forEach
                if (parentEntryId !in underlayDrivers) {
                    underlayDrivers[parentEntryId] = candidateTransition
                }
            }

            visibleEntries.forEachIndexed { index, entry ->
                key(entry.id) {
                    val transition = entryTransitions[index]
                    val underlayDriver = underlayDrivers[entry.id]
                    val pageViewModelStoreOwner = stores.owner(entry.id)

                    LaunchedEffect(entry.id, transition.ready, transition.targetProgress) {
                        if (!transition.ready) return@LaunchedEffect

                        val targetProgress = transition.targetProgress
                        if (
                            transition.progress.value != targetProgress ||
                            transition.underlayProgress.value != targetProgress
                        ) {
                            coroutineScope {
                                launch {
                                    transition.progress.animateTo(
                                        targetValue = targetProgress,
                                        animationSpec = resolvedAnimationSpec,
                                    )
                                }
                                launch {
                                    transition.underlayProgress.animateTo(
                                        targetValue = targetProgress,
                                        animationSpec = resolvedAnimationSpec,
                                    )
                                }
                            }
                            transition.progress.snapTo(targetProgress)
                            transition.underlayProgress.snapTo(targetProgress)
                        }
                        if (transition.targetProgress != targetProgress) return@LaunchedEffect

                        transition.isPageTransitioning = false
                        if (targetProgress == 0f && transition.progress.value == 0f) {
                            release(entry)
                        }
                    }

                    Box(
                        modifier = transitionModifier(
                            Modifier
                                .fillMaxSize()
                                .thenIf(index != 0) {
                                    navigationPageReady { transition.ready = true }
                                }
                                .graphicsLayer {
                                    val ownTranslation = if (index == 0) {
                                        0f
                                    } else {
                                        hostWidthPx * (1f - transition.progress.value)
                                    }
                                    translationX = ownTranslation + underlayTranslation(
                                        hostWidthPx,
                                        underlayDriver?.underlayProgress?.value ?: 0f,
                                    )
                                },
                            transition.isPageTransitioning,
                        ).zIndex(index.toFloat()),
                    ) {
                        if (index != 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(entry.id) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                awaitPointerEvent()
                                            }
                                        }
                                    },
                            )
                        }

                        pageStateHolder.SaveableStateProvider(key = entry.id) {
                            CompositionLocalProvider(
                                LocalLifecycleOwner provides transition.lifecycleOwner,
                                LocalViewModelStoreOwner provides pageViewModelStoreOwner,
                            ) {
                                if (index != 0) {
                                    PlatformBackHandler(onBack = ::closeTop)
                                }
                                NavigationScope(
                                    entry = entry,
                                    navigateTo = ::open,
                                    goBack = ::closeTop,
                                ).content()
                            }
                        }

                        if (underlayDriver != null && scrimAlpha > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = underlayDriver.underlayProgress.value
                                    }
                                    .background(scrimColor.copy(alpha = scrimAlpha)),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.navigationPageReady(onReady: () -> Unit): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        onReady()
        layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    }

private fun Modifier.thenIf(condition: Boolean, block: Modifier.() -> Modifier): Modifier {
    return if (condition) block() else this
}
