package com.nevoit.material.theme

import android.content.res.Configuration
import com.nevoit.xdnext.core.platform.PlatformContext

/**
 * Reads the night mode out of the application's configuration.
 *
 * The application context rather than an activity's, because this is asked before there is a composition
 * to read one from — the start-up warm-up builds the palettes for the mode the app is about to draw in.
 * `isSystemInDarkTheme()` answers the same question inside the composition, from the same `uiMode` flag.
 */
actual fun systemIsDarkTheme(): Boolean {
    val uiMode = PlatformContext.applicationContext.resources.configuration.uiMode
    return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}
