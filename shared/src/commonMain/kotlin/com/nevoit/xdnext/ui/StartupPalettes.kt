package com.nevoit.xdnext.ui

import androidx.compose.ui.graphics.Color
import com.nevoit.material.theme.MaterialColors
import com.nevoit.material.theme.materialColorsFromSeed
import com.nevoit.xdnext.ui.timetable.ClassPalette
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The palettes the app draws with, built on a worker once per process and handed to the first frame.
 *
 * Both palettes — the theme's, and the sixteen course colours — are generated from one seed by the same
 * library, and building them is not cheap on a device. Measured on this project's phone in a debug build:
 * a first theme palette costs about 52 ms and the course palette about 57 ms, and almost none of that is
 * arithmetic we could do less of; it is class loading followed by interpreted colour maths. It is also
 * work the *first frame* used to do, which is the one frame that has nothing else to show while it waits.
 *
 * So the start-up warm-up calls [prepare] with the platform's own seed, on a worker, at
 * `Application.onCreate` — concurrently with the activity being created. The composition resolves this
 * from the container *before* it builds anything, and the container's lock is what makes that a hand-off
 * rather than a second copy: if the worker is still building, the frame waits there, and what it then draws
 * is the real palette. Nothing is thrown away, which is what the earlier arrangement got wrong — it warmed
 * the maths with palettes nobody drew and then built the real ones on the frame anyway.
 *
 * **Prepared, or built here.** A wallpaper change after start-up makes the seed the frame reads differ from
 * the one prepared; that case builds the palette on the spot, because that is the only correct answer, and
 * it is the same call the theme's own default made before any of this existed.
 */
class StartupPalettes {

    private data class Prepared(
        val seed: Color,
        val light: MaterialColors,
        val dark: MaterialColors,
        val course: ClassPalette,
    )

    private val prepared = MutableStateFlow<Prepared?>(null)

    /**
     * Builds and publishes the palettes for [seed]. Called by the start-up warm-up, on its worker.
     *
     * Both theme modes are built here although only one will be drawn: the mode is not known off the main
     * thread, the second palette costs a fraction of the first (the colour maths is loaded by then), and
     * guessing wrong would mean drawing the wrong theme rather than a slower frame.
     */
    fun prepare(seed: Color) {
        prepared.value = Prepared(
            seed = seed,
            light = materialColorsFromSeed(seed, isDark = false),
            dark = materialColorsFromSeed(seed, isDark = true),
            course = ClassPalette.from(seed),
        )
    }

    /** The theme palette for [seedColor] and [isDarkTheme]. */
    fun themeColors(seedColor: Color, isDarkTheme: Boolean): MaterialColors {
        val current = prepared.value?.takeIf { it.seed == seedColor }
            ?: return materialColorsFromSeed(seedColor, isDarkTheme)

        return if (isDarkTheme) current.dark else current.light
    }

    /** The sixteen course colours for [seedColor]. */
    fun courseColors(seedColor: Color): ClassPalette =
        prepared.value?.takeIf { it.seed == seedColor }?.course ?: ClassPalette.from(seedColor)
}
