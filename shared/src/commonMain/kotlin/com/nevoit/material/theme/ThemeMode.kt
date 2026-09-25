package com.nevoit.material.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

private fun resolveDarkTheme(mode: ThemeMode, systemInDarkTheme: Boolean): Boolean {
    return when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> systemInDarkTheme
    }
}

@Composable
fun resolveDarkTheme(mode: ThemeMode): Boolean {
    return resolveDarkTheme(mode = mode, systemInDarkTheme = isSystemInDarkTheme())
}

/**
 * Whether the platform is in dark mode, for a caller with no composition — the start-up warm-up.
 *
 * The palettes are built before the first frame rather than in it, and the course palette builds only the
 * mode it is told to (see `ClassPalette`), so the mode has to be readable at `Application.onCreate` time.
 * It is the same fact `resolveDarkTheme(ThemeMode.SYSTEM)` reads through `isSystemInDarkTheme()`, which
 * needs a composition to ask.
 */
expect fun systemIsDarkTheme(): Boolean

