package com.nevoit.material.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * Android's back handling: the system back gesture, predictive back animations included.
 *
 * Using the platform's own handler rather than a multiplatform one is the point — Android keeps
 * improving its back gesture, and this delegates to whatever the platform does instead of pinning a
 * copy of that behaviour here.
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}