package com.nevoit.xdnext.ui.timetable

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.blend.Blend
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot
import com.nevoit.material.theme.tokens.Red500
import com.nevoit.material.theme.tokens.Stone500
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests for the generated course palette and for the one opacity rule left in the card.
 *
 * The palette is arithmetic over generated schemes now rather than a table, so what is worth pinning
 * changed with it: that the sixteen seeds and their order survive, that a swatch really is the four
 * roles of its *harmonized* seed rather than of the seed as it is, that harmony pulls towards the theme
 * without ever pushing away, and that the light and dark swatches flip the way a Material container
 * pair has to. The HSL tests that used to live here went with the adjustments themselves.
 *
 * The second thing pinned here is that the completed-class styling is *off* by default and that
 * turning it off really does leave a finished class looking identical to a running one — the
 * original's own default, and one that is easy to "fix" into a visible difference by mistake.
 */
class ClassPaletteTest {

    private val themeSeed = Color(0xFF3F51B5)
    private val palette = ClassPalette.from(themeSeed)
    private val plain = TimetableAppearance()

    @Test
    fun keepsTheColorCardsSixteenSeedsInOrder() {
        // The order is the visible thing: a timetable's first course is red and its sixteenth is
        // stone, and reordering the seeds would recolour every timetable in the app.
        assertEquals(16, ClassPalette.Seeds.size)
        assertEquals(Red500, ClassPalette.Seeds.first(), "The first is red.")
        assertEquals(Stone500, ClassPalette.Seeds.last(), "The last is stone.")
    }

    @Test
    fun everySeedGetsItsFourRolesInBothThemes() {
        assertEquals(ClassPalette.Seeds.size, palette.light.size)
        assertEquals(ClassPalette.Seeds.size, palette.dark.size)
    }

    @Test
    fun wrapsAnIndexAroundThePalette() {
        // The original wrote `colorList[index % length]`, which throws on a negative index; this wraps,
        // because a colour is not worth taking the page down for and only a bad row produces one.
        assertEquals(palette.swatchFor(0, false), palette.swatchFor(16, false))
        assertEquals(palette.swatchFor(0, false), palette.swatchFor(-16, false))
        assertEquals(palette.swatchFor(15, false), palette.swatchFor(-1, false))
    }

    @Test
    fun eachSwatchIsTheFourRolesOfItsHarmonizedSeed() {
        // The whole contract in one place: harmonize the seed against the theme, generate the app's own
        // scheme variant from that, and take these four roles — nothing else, in no other order.
        for ((index, seed) in ClassPalette.Seeds.withIndex()) {
            for (isDark in listOf(false, true)) {
                val expected = SchemeTonalSpot(
                    sourceColorHct = Hct.fromInt(Blend.harmonize(seed.toArgb(), themeSeed.toArgb())),
                    isDark = isDark,
                    contrastLevel = 0.0,
                )
                val swatch = palette.swatchFor(index, isDark = isDark)

                assertEquals(Color(expected.primary), swatch.primary, "primary of seed $index")
                assertEquals(
                    Color(expected.primaryContainer),
                    swatch.primaryContainer,
                    "primaryContainer of seed $index",
                )
                assertEquals(Color(expected.onPrimary), swatch.onPrimary, "onPrimary of seed $index")
                assertEquals(
                    Color(expected.onPrimaryContainer),
                    swatch.onPrimaryContainer,
                    "onPrimaryContainer of seed $index",
                )
            }
        }
    }

    @Test
    fun aSwatchComesFromTheHarmonizedSeedAndNotFromTheSeedItself() {
        // Without this, a generator that quietly forgot to harmonize would still pass the test above
        // for whichever seeds already share the theme's hue.
        val seed = ClassPalette.Seeds.first()
        val raw = SchemeTonalSpot(
            sourceColorHct = Hct.fromInt(seed.toArgb()),
            isDark = false,
            contrastLevel = 0.0,
        )

        assertNotEquals(
            Color(raw.primary),
            palette.swatchFor(0, isDark = false).primary,
            "The red swatch must be the red seed pulled towards the theme, not the red seed.",
        )
    }

    @Test
    fun harmonyNudgesEverySeedTowardsTheThemeAndNeverRepaintsIt() {
        val themeHue = Hct.fromInt(themeSeed.toArgb()).hue
        var movedSomething = false

        for (seed in ClassPalette.Seeds) {
            val seedHct = Hct.fromInt(seed.toArgb())
            val before = hueDistance(seedHct.hue, themeHue)
            val after = hueDistance(
                Hct.fromInt(Blend.harmonize(seed.toArgb(), themeSeed.toArgb())).hue,
                themeHue,
            )
            val shift = before - after

            // Harmony pulls towards the theme's hue and never pushes away from it, so a course's colour
            // can never end up further from the accent than it started.
            assertTrue(shift >= -1e-6, "Seed $seed was pushed away from the theme: $before° -> $after°")
            // And it is a nudge rather than a repaint. Material caps the rotation at 15°, and putting
            // the rotated colour back into sRGB costs a fraction more — the worst of these seeds is
            // 15.21°, so a bound of 16° still catches a harmonization that had been turned into a
            // blend, or a cap that had been raised.
            assertTrue(shift <= 16.0, "Seed $seed moved ${shift}°, which is a repaint, not a nudge.")

            // The formula behind the nudge is `min(half the hue difference, 15°)`, checked only where
            // a hue is a meaningful measurement: the last seed is nearly neutral, and for a colour with
            // almost no chroma the hue a round trip reports is close to noise, so it is held to the two
            // bounds above and not to the formula.
            if (seedHct.chroma >= 20) {
                val expected = minOf(before / 2.0, 15.0)
                assertTrue(
                    abs(shift - expected) <= 1.5,
                    "Seed $seed should rotate ${expected}° towards the theme, but rotated ${shift}°.",
                )
            }

            if (shift > 1e-6) movedSomething = true
        }

        assertTrue(movedSomething, "No seed moved at all, so this palette is not harmonized.")
    }

    @Test
    fun lightAndDarkSwatchesFlipTheContainerPair() {
        for (index in ClassPalette.Seeds.indices) {
            val light = palette.swatchFor(index, isDark = false)
            val dark = palette.swatchFor(index, isDark = true)

            assertTrue(
                light.primaryContainer.luminance() > light.onPrimaryContainer.luminance(),
                "A light card is a pale container with dark content on it (seed $index).",
            )
            assertTrue(
                dark.primaryContainer.luminance() < dark.onPrimaryContainer.luminance(),
                "A dark card is a dark container with light content on it (seed $index).",
            )
            assertNotEquals(light, dark, "The two themes must not be the same palette.")
        }
    }

    @Test
    fun thePaletteFollowsTheThemeSeed() {
        val indigo = ClassPalette.from(Color(0xFF3F51B5))
        val teal = ClassPalette.from(Color(0xFF00897B))

        assertNotEquals(indigo.light, teal.light)
        assertNotEquals(
            indigo.swatchFor(0, isDark = false),
            teal.swatchFor(0, isDark = false),
        )
    }

    @Test
    fun aFinishedClassLooksLikeARunningOneUntilTheSettingIsTurnedOn() {
        assertEquals(plain.cardAlpha(isCompleted = false), plain.cardAlpha(isCompleted = true))
    }

    @Test
    fun turningTheSettingOnDimsAFinishedClassAndLeavesARunningOneAlone() {
        val appearance = TimetableAppearance(completedClassStyleEnabled = true)

        assertEquals(
            1f,
            appearance.cardAlpha(isCompleted = false),
            "A class still running must not move when the setting changes.",
        )
        assertEquals(0.5f, appearance.completedAlpha)
        assertEquals(appearance.completedAlpha, appearance.cardAlpha(isCompleted = true))
    }
}

/** How far apart two HCT hues are, the short way round the circle. */
private fun hueDistance(a: Double, b: Double): Double = 180.0 - abs(abs(a - b) - 180.0)
