package com.nevoit.material.theme

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.nevoit.xdnext.core.platform.PlatformContext

/**
 * Reads the wallpaper-derived system accent as the seed colour.
 *
 * `android.R.color.system_accent1_600` is the same tone Material 3 uses for `primary` in its own
 * dynamic schemes, which is why it is the right seed rather than `system_accent1_500`: generating
 * from it keeps our primary close to the system's.
 *
 * The resource only exists from Android 12 (API 31), while this app runs from API 29, so older
 * devices fall back to the Material 3 baseline seed instead of throwing
 * `Resources.NotFoundException`.
 *
 * The context is a key of the `remember`, so a configuration change that hands us a new context
 * (and with it a new wallpaper accent) re-reads the colour.
 */
@Composable
actual fun rememberSystemSeedColor(): Color {
    val context = LocalContext.current

    return remember(context) { systemSeedColor() }
}

/**
 * [rememberSystemSeedColor] without a composition.
 *
 * The application context rather than the activity's, because there is none: this is read by the start-up
 * warm-up, which builds the palettes on a worker before the first frame exists. For a framework colour
 * resource the two contexts answer the same, and the composition's own read — this same function — is what
 * decides whether the prepared palettes are the ones it wants.
 */
actual fun systemSeedColor(): Color =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Color(PlatformContext.applicationContext.getColor(android.R.color.system_accent1_600))
    } else {
        FallbackSeedColor
    }
