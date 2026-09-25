package com.nevoit.xdnext.ui

import androidx.compose.ui.graphics.Color
import com.nevoit.material.theme.MaterialColors
import com.nevoit.material.theme.materialColorsFromSeed
import com.nevoit.material.theme.systemIsDarkTheme
import com.nevoit.material.theme.systemSeedColor
import com.nevoit.xdnext.ui.timetable.ClassPalette
import com.nevoit.xdnext.ui.timetable.ClassPaletteCache

/**
 * The palettes the app draws with, built once per process by whoever gets there first.
 *
 * Both of them — the theme's, and the sixteen course colours — are generated from one seed, and generating
 * them is not cheap on a device: measured at about 40 ms for the theme palette and **180 ms** for the
 * course palette, all of it interpreted colour maths. Neither is optional. The first frame paints with the
 * theme's palette, and the campus page draws today's classes in the course colours, so a palette that
 * arrives after the first frame is a frame drawn in colours nothing has — the one outcome that is not
 * allowed.
 *
 * Three things make that affordable, and they layer:
 *
 *  1. **The work happens here, in the constructor.** The start-up warm-up resolves this on a worker at
 *     `Application.onCreate`, with the platform's own seed and mode, concurrently with the activity being
 *     created; the composition resolves the same instance before it draws anything, and the container's
 *     creation lock turns that into a hand-off — if the worker is still building, the frame waits there
 *     instead of building a second copy. Same work either way, but not on the main thread.
 *  2. **The course colours are cached** by seed ([ClassPaletteCache], injected). That is the expensive half
 *     and a pure function of the accent, so after the first launch the worker's whole job is the theme
 *     palette — about 40 ms, comfortably inside the window before the first composition asks for it.
 *  3. **Only the mode being drawn is generated** when there is nothing cached: the other theme's sixteen
 *     schemes are wanted only when the mode changes, and [ClassPalette] generates them on first use.
 *
 * The cache is filled with **both** themes when it is written, deliberately: a file holding only the mode of
 * the launch that wrote it would miss on every launch in the other mode, which is the opposite of the point.
 *
 * A wallpaper change moves the seed away from the one prepared — and from the one cached. Both readers then
 * build for the seed they were asked about, because that is the only correct answer, and the cache's own
 * check is what keeps the file from outliving the accent it describes.
 */
class StartupPalettes(
    private val seed: Color = systemSeedColor(),
    isDarkTheme: Boolean = systemIsDarkTheme(),
    private val cache: ClassPaletteCache? = null,
) {

    private val light: MaterialColors = materialColorsFromSeed(seed, isDark = false)

    private val dark: MaterialColors = materialColorsFromSeed(seed, isDark = true)

    /** The course colours: read from the cache, or generated and written to it. */
    private val course: ClassPalette = cachedCourse() ?: generatedCourse(isDarkTheme)

    private fun cachedCourse(): ClassPalette? =
        cache?.read(seed)?.let { ClassPalette.of(seed, light = it.light, dark = it.dark) }

    private fun generatedCourse(isDarkTheme: Boolean): ClassPalette =
        ClassPalette.from(seed).also { palette ->
            if (cache == null) {
                // Nothing to fill, so only the mode about to be drawn is generated.
                palette.swatches(isDarkTheme)
            } else {
                cache.save(seed, light = palette.light, dark = palette.dark)
            }
        }

    /** The theme palette for [seedColor] and [isDarkTheme]. */
    fun themeColors(seedColor: Color, isDarkTheme: Boolean): MaterialColors = when {
        seedColor != seed -> materialColorsFromSeed(seedColor, isDarkTheme)
        isDarkTheme -> dark
        else -> light
    }

    /** The sixteen course colours for [seedColor]. */
    fun courseColors(seedColor: Color): ClassPalette =
        if (seedColor == seed) course else ClassPalette.from(seedColor)
}
