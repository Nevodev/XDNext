package com.nevoit.xdnext.ui

import androidx.compose.ui.graphics.Color
import com.nevoit.material.theme.materialColorsFromSeed
import com.nevoit.xdnext.ui.timetable.ClassPalette
import com.nevoit.xdnext.ui.timetable.ClassPaletteCache
import kotlinx.serialization.json.Json
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * Tests for the palettes the first frame is handed.
 *
 * The class exists so that the frame never generates a palette itself, and the assertions that matter are
 * therefore about *identity*: a reader that gets back the same instance the constructor built is a reader
 * that did no work, and `assertSame` is what says so — an equality assertion would pass while the frame was
 * still generating its own copy.
 *
 * The seed and mode are passed explicitly here. In the app they default to the platform's own, which is
 * what lets the warm-up build for the mode the app is about to draw in; a test has no platform.
 */
class StartupPalettesTest {

    private val seed = Color(0xFF1B6EF3)
    private val otherSeed = Color(0xFFD32F2F)
    private val palettes = StartupPalettes(seed = seed, isDarkTheme = false)

    @Test
    fun buildsTheThemesPaletteForBothModes() {
        // Both, although only one is drawn: the second costs a fraction of the first, and a frame that
        // resolved to the other mode must never be handed the wrong theme.
        assertEquals(materialColorsFromSeed(seed, isDark = false), palettes.themeColors(seed, isDarkTheme = false))
        assertEquals(materialColorsFromSeed(seed, isDark = true), palettes.themeColors(seed, isDarkTheme = true))
        assertNotEquals(
            palettes.themeColors(seed, isDarkTheme = false),
            palettes.themeColors(seed, isDarkTheme = true),
        )
    }

    @Test
    fun handsThePreparedThemePalettesOverRatherThanBuildingThemAgain() {
        assertSame(
            palettes.themeColors(seed, isDarkTheme = false),
            palettes.themeColors(seed, isDarkTheme = false),
            "A prepared palette must be handed over, not generated again for every reader.",
        )
        assertSame(
            palettes.themeColors(seed, isDarkTheme = true),
            palettes.themeColors(seed, isDarkTheme = true),
        )
    }

    @Test
    fun handsThePreparedCourseColoursOver() {
        assertEquals(ClassPalette.from(seed), palettes.courseColors(seed))
        assertSame(palettes.courseColors(seed), palettes.courseColors(seed))
    }

    @Test
    fun theCourseColoursOfTheOtherModeAreGeneratedWhenTheyAreAskedFor() {
        // Only the mode being drawn is generated up front, so the dark half is built on first use. That
        // must be invisible to whoever draws in dark mode: the colours are the ones the generator gives,
        // whichever half was built first.
        val course = palettes.courseColors(seed)

        assertEquals(
            ClassPalette.from(seed).swatchFor(index = 3, isDark = true),
            course.swatchFor(index = 3, isDark = true),
        )
    }

    @Test
    fun aDifferentSeedIsBuiltRatherThanMistakenForThePreparedOne() {
        // A wallpaper change moves the seed away from the one the warm-up prepared; answering with the
        // prepared palettes would repaint the app in colours nobody chose.
        assertEquals(
            materialColorsFromSeed(otherSeed, isDark = true),
            palettes.themeColors(otherSeed, isDarkTheme = true),
        )
        assertEquals(ClassPalette.from(otherSeed), palettes.courseColors(otherSeed))
        assertNotEquals(palettes.courseColors(seed), palettes.courseColors(otherSeed))
    }

    @Test
    fun usesWhatTheCacheHoldsForThisAccent() {
        // The cache is the expensive half of the work, so a hit has to actually be used. The file is
        // deliberately filled with *another* seed's colours — a construction that ignored the cache would
        // hand back this seed's, so these two lists are what tell the two apart. (`ClassPalette` compares
        // by seed, which is why the colours themselves are asserted rather than the palettes.)
        val cache = cache()
        val fromTheFile = ClassPalette.from(otherSeed)
        cache.save(seed, light = fromTheFile.light, dark = fromTheFile.dark)

        val handedOver = StartupPalettes(seed = seed, isDarkTheme = false, cache = cache)
            .courseColors(seed)

        assertEquals(fromTheFile.light, handedOver.light)
        assertEquals(fromTheFile.dark, handedOver.dark)
        assertNotEquals(ClassPalette.from(seed).light, handedOver.light)
    }

    @Test
    fun fillsTheCacheForBothThemesOnTheWayPast() {
        // A file holding only the mode that happened to write it would miss on every launch in the other
        // mode. This is the other half of the same rule, checked from the file's side.
        val cache = cache()
        val generated = ClassPalette.from(seed)

        StartupPalettes(seed = seed, isDarkTheme = false, cache = cache)
        val written = assertNotNull(cache.read(seed))

        assertEquals(generated.light, written.light)
        assertEquals(generated.dark, written.dark)
    }

    @Test
    fun theSecondLaunchReadsTheFileInsteadOfGenerating() {
        val cache = cache()

        StartupPalettes(seed = seed, isDarkTheme = true, cache = cache)
        // The first construction wrote the file; this one has to come out of it, in either mode.
        val second = StartupPalettes(seed = seed, isDarkTheme = false, cache = cache)

        assertEquals(ClassPalette.from(seed), second.courseColors(seed))
        assertSame(second.courseColors(seed), second.courseColors(seed))
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private fun cache(): ClassPaletteCache = ClassPaletteCache(
        fileSystem = FileSystem.SYSTEM,
        path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "xdnext-startup-palettes-${instances++}" /
                "ClassPalette.json",
        json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false },
    )

    private companion object {
        /** One file per test, so no two of them share a cache. */
        var instances = 0
    }
}
