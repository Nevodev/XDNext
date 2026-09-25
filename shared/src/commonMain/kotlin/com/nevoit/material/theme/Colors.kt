package com.nevoit.material.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeTonalSpot
import com.nevoit.material.theme.utility.purify
import com.nevoit.material.theme.utility.umamify

@Immutable
data class MaterialColors(
    val background: Color,

    // for switches
    val activeTrack: Color,
    val inactiveTrack: Color,
    val activeThumb: Color,
    val inactiveThumb: Color,

    // scrim
    val scrimLight: Color,
    val scrimNormal: Color,
    val scrimMedium: Color,
    val scrimBold: Color,

    // for hierarchical cards
    val pageBackground: Color,
    val cardBackground: Color,

    // for bottom sheets
    val elevatedPageBackground: Color,
    val elevatedCardBackground: Color,

    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val tertiary: Color,
    val onTertiary: Color,

    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,

    // main font colors
    val content: Color,
    val contentVariant: Color,

    val highlightText: Color,

    // error color
    val error: Color,
    val onError: Color,

    // The error container pair: what a *filled* box uses to say "this one is wrong or running low".
    // Without it the call sites had to fake a container by compositing `error` at low alpha over the
    // card, which is invisible in dark mode, where the card is already near-black.
    val errorContainer: Color,
    val onErrorContainer: Color,

    // for segmented control
    val segmentedControlBackground: Color = scrimNormal,
    val onSegmentedControlBackground: Color = contentVariant,
    val segmentedControlIndicator: Color,
    val onSegmentedControlIndicator: Color = content,

    // for the navigation bar, named after the Material 3 tokens they come from:
    // `md.comp.navigation-bar.active-indicator.color`, `.active-icon.color`, `.active-label-text.color`,
    // `.inactive-icon.color` and `.inactive-label-text.color`. The container token is deliberately
    // absent: this bar paints no container.
    val navigationBarIndicator: Color,
    val onNavigationBarIndicator: Color,
    val navigationBarActiveLabel: Color,
    val navigationBarInactiveIcon: Color,
    val navigationBarInactiveLabel: Color,

    val shadow: Color
)

/**
 * Generates the whole palette from one seed colour.
 *
 * The scheme is the Material 3 `TonalSpot` variant — the same variant, spec version and contrast
 * level the system itself uses for wallpaper colours — so the palette is a Material You palette and
 * not a hand-tuned approximation of one.
 *
 * This deliberately goes through `material-color-utilities` rather than any Compose colour-scheme
 * type: the roles below are read as plain ARGB values, which keeps the theme independent of the
 * official Material 3 artifacts.
 */
fun materialColorsFromSeed(seedColor: Color, isDark: Boolean): MaterialColors =
    materialColorsFromScheme(
        scheme = SchemeTonalSpot(
            sourceColorHct = Hct.fromInt(seedColor.toArgb()),
            isDark = isDark,
            contrastLevel = 0.0,
        ),
        isDark = isDark,
    )

/**
 * Derives this package's [MaterialColors] from a generated [scheme].
 *
 * The two Oklch adjustments are the only deliberate departures from the scheme, and they exist
 * because the raw roles do not read correctly at these use sites: [purify] pulls highlighted text off
 * the fully saturated tertiary tone, and [umamify] puts back saturation the error role loses.
 *
 * The background hierarchy is intentional rather than symmetric. In light mode the page sits below
 * the cards (a container tone against `surface`), while in dark mode the page is pure black and the
 * cards are lifted out of it by surface container tones. That is what the elevation look depends on,
 * so the two branches are written out separately instead of being collapsed into one expression.
 */
fun materialColorsFromScheme(scheme: DynamicScheme, isDark: Boolean): MaterialColors {
    val pageBackground = if (isDark) Color.Black else Color(scheme.surfaceContainer)
    val cardBackground = if (isDark) Color(scheme.surfaceContainer) else Color(scheme.surface)
    val background = if (isDark) pageBackground else cardBackground

    val pageBackgroundElevated = if (isDark) Color(scheme.surfaceContainer) else pageBackground
    val cardBackgroundElevated =
        if (isDark) Color(scheme.surfaceContainerHigh) else cardBackground

    val contentColor = if (isDark) Color.White else Color.Black

    val scrimLight = contentColor.copy(alpha = if (isDark) 0.05f else 0.025f)
    val scrimNormal = contentColor.copy(alpha = if (isDark) 0.1f else 0.05f)
    val scrimMedium = contentColor.copy(alpha = if (isDark) 0.2f else 0.1f)
    val scrimBold = contentColor.copy(alpha = if (isDark) 0.4f else 0.2f)

    return MaterialColors(
        background = background,
        activeTrack = Color(scheme.primary),
        inactiveTrack = Color(scheme.surfaceContainerHighest),
        activeThumb = Color(scheme.onPrimary),
        inactiveThumb = Color(scheme.outline),
        pageBackground = pageBackground,
        cardBackground = cardBackground,
        elevatedPageBackground = pageBackgroundElevated,
        elevatedCardBackground = cardBackgroundElevated,
        scrimLight = scrimLight,
        scrimNormal = scrimNormal,
        scrimMedium = scrimMedium,
        scrimBold = scrimBold,
        primary = Color(scheme.primary),
        onPrimary = Color(scheme.onPrimary),
        secondary = Color(scheme.secondary),
        onSecondary = Color(scheme.onSecondary),
        tertiary = Color(scheme.tertiary),
        onTertiary = Color(scheme.onTertiary),
        primaryContainer = Color(scheme.primaryContainer),
        onPrimaryContainer = Color(scheme.onPrimaryContainer),
        secondaryContainer = Color(scheme.secondaryContainer),
        onSecondaryContainer = Color(scheme.onSecondaryContainer),
        tertiaryContainer = Color(scheme.tertiaryContainer),
        onTertiaryContainer = Color(scheme.onTertiaryContainer),
        content = contentColor,
        contentVariant = contentColor.copy(alpha = .5f),
        highlightText = Color(scheme.tertiary).purify(0.8f),
        error = Color(scheme.error).umamify(1.2f),
        onError = Color(scheme.onError).umamify(1.2f),
        // Umamified like the two above, so the whole error family shares one treatment: the container
        // and the glyph it carries have to read as the same colour, not as two different reds.
        errorContainer = Color(scheme.errorContainer).umamify(1.2f),
        onErrorContainer = Color(scheme.onErrorContainer).umamify(1.2f),
        segmentedControlBackground = Color(scheme.secondaryContainer),
        onSegmentedControlBackground = Color(scheme.onSecondaryContainer),
        segmentedControlIndicator = Color(scheme.secondary),
        onSegmentedControlIndicator = Color(scheme.onSecondary),
        // The one navigation-bar role that is *not* the segmented control's under another name: the
        // spec puts the active label on `onSurface` while the active icon sits on the indicator's
        // container, so the two active colours intentionally differ.
        navigationBarIndicator = Color(scheme.secondaryContainer),
        onNavigationBarIndicator = Color(scheme.onSecondaryContainer),
        navigationBarActiveLabel = Color(scheme.onSurface),
        navigationBarInactiveIcon = Color(scheme.onSurfaceVariant),
        navigationBarInactiveLabel = Color(scheme.onSurfaceVariant),
        shadow = Color.Black
    )
}

/**
 * What the composition locals fall back to when a component is read outside [MaterialTheme].
 *
 * The vendored components take their colours from `compositionLocalOf` rather than from parameters,
 * and their defaults cannot be `null`, so they need some complete palette. Generating it from the
 * same baseline seed the theme falls back to keeps the answer consistent: reaching this value means
 * the theme was not applied, not that a second palette exists.
 *
 * **Lazy, and that is not a detail.** A top-level `val` is initialised when its file is first touched,
 * and [materialColorsFromSeed] is in this file — so as an eager `val` this generated a whole second
 * scheme, and the eight Oklch adjustments that go with it, the first time the theme built a palette,
 * on the main thread, during the first composition, only for the result to be thrown away. Nothing in
 * the app reads it: the theme always provides a palette. This way it costs nothing until a component
 * really is drawn outside the theme.
 */
internal val MaterialColorsDefault: MaterialColors by lazy {
    materialColorsFromSeed(FallbackSeedColor, isDark = false)
}
