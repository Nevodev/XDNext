package com.nevoit.material.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeTonalSpot
import com.nevoit.material.theme.utility.purify
import com.nevoit.material.theme.utility.umamify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The palette is the one part of the theme that is pure arithmetic over a generated colour scheme, so
 * it is the part worth pinning: every role below is either copied straight from the scheme or is one
 * of the two documented Oklch adjustments, and a silent mix-up here would only show up as slightly
 * wrong colours on a device.
 */
class MaterialColorsTest {

    private val seed = Color(0xFF3F51B5)

    private fun scheme(isDark: Boolean): DynamicScheme = SchemeTonalSpot(
        sourceColorHct = Hct.fromInt(seed.toArgb()),
        isDark = isDark,
        contrastLevel = 0.0,
    )

    @Test
    fun theSeedIsWhatSelectsTheScheme() {
        val fromSeed = materialColorsFromSeed(seed, isDark = false)

        assertEquals(materialColorsFromScheme(scheme(isDark = false), isDark = false), fromSeed)
    }

    @Test
    fun lightPaletteTakesItsRolesFromTheScheme() {
        val scheme = scheme(isDark = false)
        val colors = materialColorsFromScheme(scheme, isDark = false)

        assertEquals(Color(scheme.primary), colors.primary)
        assertEquals(Color(scheme.onPrimary), colors.onPrimary)
        assertEquals(Color(scheme.primaryContainer), colors.primaryContainer)
        assertEquals(Color(scheme.onPrimaryContainer), colors.onPrimaryContainer)
        assertEquals(Color(scheme.primary), colors.activeTrack)
        assertEquals(Color(scheme.onPrimary), colors.activeThumb)
        assertEquals(Color(scheme.surfaceContainerHighest), colors.inactiveTrack)
        assertEquals(Color(scheme.outline), colors.inactiveThumb)

        assertEquals(Color(scheme.secondaryContainer), colors.segmentedControlBackground)
        assertEquals(Color(scheme.onSecondaryContainer), colors.onSegmentedControlBackground)
        assertEquals(Color(scheme.secondary), colors.segmentedControlIndicator)
        assertEquals(Color(scheme.onSecondary), colors.onSegmentedControlIndicator)

        assertEquals(Color(scheme.secondaryContainer), colors.navigationBarIndicator)
        assertEquals(Color(scheme.onSecondaryContainer), colors.onNavigationBarIndicator)
        assertEquals(Color(scheme.onSurface), colors.navigationBarActiveLabel)
        assertEquals(Color(scheme.onSurfaceVariant), colors.navigationBarInactiveIcon)
        assertEquals(Color(scheme.onSurfaceVariant), colors.navigationBarInactiveLabel)

        assertEquals(Color(scheme.tertiary).purify(0.8f), colors.highlightText)
        assertEquals(Color(scheme.error).umamify(1.2f), colors.error)
        assertEquals(Color(scheme.onError).umamify(1.2f), colors.onError)
        assertEquals(Color(scheme.errorContainer).umamify(1.2f), colors.errorContainer)
        assertEquals(Color(scheme.onErrorContainer).umamify(1.2f), colors.onErrorContainer)
    }

    @Test
    fun lightPaletteSitsCardsAboveTheContainerTonePage() {
        val scheme = scheme(isDark = false)
        val colors = materialColorsFromScheme(scheme, isDark = false)

        assertEquals(Color(scheme.surfaceContainer), colors.pageBackground)
        assertEquals(Color(scheme.surface), colors.cardBackground)
        assertEquals(Color(scheme.surfaceContainer), colors.elevatedPageBackground)
        // Elevated surfaces only differ in dark mode; in light mode they keep the card colour.
        assertEquals(Color(scheme.surface), colors.elevatedCardBackground)
        // The single background the shell paints is the card colour in light mode.
        assertEquals(Color(scheme.surface), colors.background)
    }

    @Test
    fun darkPaletteLiftsCardsOutOfPureBlack() {
        val scheme = scheme(isDark = true)
        val colors = materialColorsFromScheme(scheme, isDark = true)

        assertEquals(Color.Black, colors.pageBackground)
        assertEquals(Color.Black, colors.background)
        assertEquals(Color(scheme.surfaceContainer), colors.cardBackground)
        assertEquals(Color(scheme.surfaceContainer), colors.elevatedPageBackground)
        assertEquals(Color(scheme.surfaceContainerHigh), colors.elevatedCardBackground)
    }

    @Test
    fun contentIsOpaqueBlackOrWhiteWithContentScrimsDerivedFromIt() {
        val light = materialColorsFromScheme(scheme(isDark = false), isDark = false)
        assertEquals(Color.Black, light.content)
        assertEquals(Color.Black.copy(alpha = .5f), light.contentVariant)
        assertEquals(Color.Black, light.shadow)
        assertEquals(Color.Black.copy(alpha = 0.025f), light.scrimLight)
        assertEquals(Color.Black.copy(alpha = 0.05f), light.scrimNormal)
        assertEquals(Color.Black.copy(alpha = 0.1f), light.scrimMedium)
        assertEquals(Color.Black.copy(alpha = 0.2f), light.scrimBold)

        val dark = materialColorsFromScheme(scheme(isDark = true), isDark = true)
        assertEquals(Color.White, dark.content)
        assertEquals(Color.White.copy(alpha = .5f), dark.contentVariant)
        assertEquals(Color.Black, dark.shadow)
        assertEquals(Color.White.copy(alpha = 0.05f), dark.scrimLight)
        assertEquals(Color.White.copy(alpha = 0.1f), dark.scrimNormal)
        assertEquals(Color.White.copy(alpha = 0.2f), dark.scrimMedium)
        assertEquals(Color.White.copy(alpha = 0.4f), dark.scrimBold)
    }

    @Test
    fun thePaletteFollowsTheSeedColour() {
        val blue = materialColorsFromSeed(Color(0xFF1B6EF3), isDark = false)
        val red = materialColorsFromSeed(Color(0xFFD32F2F), isDark = false)

        assertNotEquals(blue.primary, red.primary)
        assertNotEquals(blue.segmentedControlIndicator, red.segmentedControlIndicator)
        assertNotEquals(blue.highlightText, red.highlightText)
    }

    @Test
    fun theDefaultsAreACompletePalette() {
        // Components read the locals with a non-null default, so the fallback must be usable.
        assertEquals(Color.Black, MaterialColorsDefault.content)
        assertNotEquals(Color.Unspecified, MaterialColorsDefault.cardBackground)
    }
}
