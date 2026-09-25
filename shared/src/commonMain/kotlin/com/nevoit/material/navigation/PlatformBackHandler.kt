package com.nevoit.material.navigation

import androidx.compose.runtime.Composable

/**
 * Handles the platform's own "go back" for the page on top of the navigation stack.
 *
 * Android routes this to the system back gesture, predictive back included, because that is the
 * behaviour a user of that platform expects. Platforms without a back gesture — desktop, where you
 * close a window instead — do nothing, so [NavigationHost] never has to ask which platform it is on.
 *
 * This is deliberately hand-rolled rather than the multiplatform `BackHandler` from
 * `org.jetbrains.compose.ui:ui-backhandler`: that one is experimental, already deprecated in favour of
 * `NavigationEventHandler`, and would be a dependency carried only for Android's sake. One `expect`
 * with an Android `actual` is smaller than the artifact it replaces and behaves better on the platform
 * we ship.
 *
 * Non-Android targets provide a no-op actual — `actual fun PlatformBackHandler(enabled: Boolean,
 * onBack: () -> Unit) = Unit` — when their targets are added.
 */
@Composable
expect fun PlatformBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
)
