package com.nevoit.xdnext.ui

import androidx.compose.ui.graphics.Color
import com.nevoit.material.theme.materialColorsFromSeed
import com.nevoit.xdnext.ui.timetable.ClassPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * Tests for the palettes the first frame is handed.
 *
 * Two rules are worth pinning, and they pull in opposite directions. The first is that preparing the
 * palettes on a worker must not change what is drawn: same seed in, same palette out, in both theme modes.
 * The second is that the frame must not pay for them twice — so when the seed the frame reads is the one
 * that was prepared, the palette it gets is the *same instance*, not an equal one built again. `assertSame`
 * is what says so; an equality assertion would pass while the frame was still doing the work.
 */
class StartupPalettesTest {

    private val seed = Color(0xFF1B6EF3)
    private val otherSeed = Color(0xFFD32F2F)
    private val palettes = StartupPalettes()

    @Test
    fun buildsWhatTheThemeWouldHaveBuiltWhenNothingWasPrepared() {
        assertEquals(
            materialColorsFromSeed(seed, isDark = false),
            palettes.themeColors(seed, isDarkTheme = false),
        )
        assertEquals(ClassPalette.from(seed), palettes.courseColors(seed))
    }

    @Test
    fun handsThePreparedPalettesOverRatherThanBuildingThemAgain() {
        palettes.prepare(seed)

        assertSame(
            palettes.themeColors(seed, isDarkTheme = false),
            palettes.themeColors(seed, isDarkTheme = false),
            "A prepared palette must be handed over, not built again for every reader.",
        )
        assertSame(
            palettes.courseColors(seed),
            palettes.courseColors(seed),
            "Same for the course colours, which are the more expensive of the two.",
        )
    }

    @Test
    fun withoutPreparationEachReaderBuildsItsOwn() {
        // The other half of the rule above, and the reason it is worth asserting identity there: with
        // nothing prepared, a palette is built per call — so `assertSame` can only pass because the worker
        // really did hand something over.
        assertNotSame(
            palettes.themeColors(seed, isDarkTheme = false),
            palettes.themeColors(seed, isDarkTheme = false),
        )
        assertNotSame(palettes.courseColors(seed), palettes.courseColors(seed))
    }

    @Test
    fun aDifferentSeedIsBuiltRatherThanMistakenForThePreparedOne() {
        // The wallpaper can change while the process is alive; the prepared palettes are for the seed the
        // worker read, and answering a different seed with them would repaint the app in colours nobody
        // chose.
        palettes.prepare(seed)

        assertEquals(
            materialColorsFromSeed(otherSeed, isDark = true),
            palettes.themeColors(otherSeed, isDarkTheme = true),
        )
        assertEquals(ClassPalette.from(otherSeed), palettes.courseColors(otherSeed))
        assertNotEquals(palettes.courseColors(seed), palettes.courseColors(otherSeed))
    }

    @Test
    fun bothThemeModesComeOutOfOnePreparation() {
        // The mode is not known on the worker, so both are built there; a reader that ignored its mode
        // would draw a light screen in dark mode and vice versa.
        palettes.prepare(seed)

        assertNotEquals(
            palettes.themeColors(seed, isDarkTheme = false),
            palettes.themeColors(seed, isDarkTheme = true),
        )
    }
}
