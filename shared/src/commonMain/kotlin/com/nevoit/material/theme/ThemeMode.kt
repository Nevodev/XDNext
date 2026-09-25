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

