package com.nevoit.material.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The colour the palette is generated from: the platform's own theme colour.
 *
 * On Android that is `android.R.color.system_accent1_600` — the wallpaper-derived accent the system
 * itself uses, which is what makes the app follow the device theme instead of shipping a palette of
 * its own.
 *
 * Declared as an `expect` because the colour is a platform resource, not a value shared code can
 * look up. Platforms without a system accent return a documented fallback so the theme is still a
 * generated one rather than a hard-coded palette.
 */
@Composable
expect fun rememberSystemSeedColor(): Color

/**
 * The same colour, for a caller with no composition — the start-up warm-up.
 *
 * The palettes are built before the first frame rather than in it (see `StartupPalettes`), and the
 * warm-up at `Application.onCreate` has no composition to ask. Reading the platform's accent resource
 * needs nothing more than a context, which the platform entry point has already published by then.
 */
expect fun systemSeedColor(): Color

/**
 * Material 3's baseline seed, for platforms (or Android versions) with no system accent colour.
 */
internal val FallbackSeedColor = Color(0xFF6750A4)
