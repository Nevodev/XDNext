package com.nevoit.xdnext.ui.timetable

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.blend.Blend
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot
import com.nevoit.material.theme.tokens.Amber500
import com.nevoit.material.theme.tokens.Blue500
import com.nevoit.material.theme.tokens.Cyan500
import com.nevoit.material.theme.tokens.Emerald500
import com.nevoit.material.theme.tokens.Green500
import com.nevoit.material.theme.tokens.Indigo500
import com.nevoit.material.theme.tokens.Lime500
import com.nevoit.material.theme.tokens.Orange500
import com.nevoit.material.theme.tokens.Purple500
import com.nevoit.material.theme.tokens.Red500
import com.nevoit.material.theme.tokens.Rose400
import com.nevoit.material.theme.tokens.Sky500
import com.nevoit.material.theme.tokens.Stone500
import com.nevoit.material.theme.tokens.Teal500
import com.nevoit.material.theme.tokens.Violet500
import com.nevoit.material.theme.tokens.Yellow500

/**
 * The timetable's own appearance settings.
 *
 * The original kept these in two static config classes,
 * `CurrentTimeIndicatorConfig` and `CompletedClassStyleConfig`, each of which read itself out of the
 * preference file and wrote itself back. They are one value here because they are one thing — how the
 * grid should look — and because a value can be handed to the drawing code in a test, which the
 * statics could not.
 *
 * Every default is the original's own, and two of them are worth knowing about:
 *
 *  - [completedClassStyleEnabled] is **false**, so by default a class that is over looks exactly like
 *    one that is not. The split machinery on the card is real and visible all the same: the "completed"
 *    portion of a class still in progress is drawn from the same colour as the rest while this is off.
 *  - [showTimeLabel] and [showTodayColumnHighlight] are **true**, so the indicator's clock face and
 *    the tinted column exist out of the box.
 *
 * The preference keys behind them (`currentTimeIndicatorEnabled`, `classStyleCompletedEnabled`, …)
 * arrive with the settings page that owns them; nothing writes them yet, so reading them here would be
 * reading defaults through a filesystem. This is the parameter that page will supply.
 *
 * The one thing that is *not* the original's is what a finished class is made of. The original took a
 * saturation and a lightness factor to each of the card's three tones in HSL; with the card's colours
 * generated rather than hand-picked (see [ClassPalette]) there is nothing left to tune that way, so a
 * finished class is now simply the same colours at [completedAlpha].
 */
data class TimetableAppearance(
    val currentTimeIndicatorEnabled: Boolean = true,
    val showTimeLabel: Boolean = true,
    val showTodayColumnHighlight: Boolean = true,

    val lineAlpha: Float = 0.9f,
    val lineThicknessDp: Float = 2f,
    val labelHeightDp: Float = 13f,
    val labelFontSizeSp: Float = 7f,
    val labelBackgroundAlpha: Float = 0.75f,
    val labelBorderRadiusDp: Float = 4f,
    val dayColumnHighlightAlpha: Float = 0.25f,
    val dayColumnHighlightRadiusDp: Float = 8f,
    val completedClassStyleEnabled: Boolean = false,
    val completedAlpha: Float = 0.5f,
) {
    fun cardAlpha(isCompleted: Boolean): Float =
        if (isCompleted && completedClassStyleEnabled) completedAlpha else 1f
}

@Immutable
data class ClassSwatch(
    val primary: Color,
    val primaryContainer: Color,
    val onPrimary: Color,
    val onPrimaryContainer: Color,
)

/**
 * The sixteen course colours, in both themes, for one seed.
 *
 * **Each theme is generated when it is first asked for, not when the palette is made.** Sixteen seeds in
 * two themes is thirty-two schemes, which measured about 180 ms of interpreted colour maths on a device —
 * and only one of the two themes is ever on screen at a time. The start-up warm-up therefore builds the
 * mode the app is about to draw (see `StartupPalettes`) and the other one is built only if the mode
 * changes, which is something a user does once in a while rather than on every launch.
 *
 * Equality is by seed, because the seed is the entire input: two palettes generated from the same colour
 * are the same palette, whether or not either has got round to generating its dark half yet.
 */
@Immutable
class ClassPalette internal constructor(
    private val themeSeed: Color,
    cachedLight: List<ClassSwatch>? = null,
    cachedDark: List<ClassSwatch>? = null,
) {

    private val key = themeSeed.toArgb()

    private val lightSwatches: List<ClassSwatch> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        cachedLight ?: Seeds.map { it.rolesFor(key, isDark = false) }
    }

    private val darkSwatches: List<ClassSwatch> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        cachedDark ?: Seeds.map { it.rolesFor(key, isDark = true) }
    }

    /** The sixteen swatches as they are drawn in the light theme. */
    val light: List<ClassSwatch> get() = lightSwatches

    /** The sixteen swatches as they are drawn in the dark theme. */
    val dark: List<ClassSwatch> get() = darkSwatches

    fun swatches(isDark: Boolean): List<ClassSwatch> = if (isDark) dark else light

    fun swatchFor(index: Int, isDark: Boolean): ClassSwatch {
        val swatches = swatches(isDark)
        val size = swatches.size
        return swatches[((index % size) + size) % size]
    }

    override fun equals(other: Any?): Boolean =
        other is ClassPalette && other.themeSeed == themeSeed

    override fun hashCode(): Int = themeSeed.hashCode()

    override fun toString(): String = "ClassPalette($themeSeed)"

    companion object {
        /**
         * The sixteen seeds, one per course, in the original's order: a timetable's first course is
         * red and its sixteenth is stone, so reordering this list recolours every timetable in the app.
         */
        val Seeds: List<Color> = listOf(
            Red500,
            Rose400,
            Purple500,
            Violet500,
            Indigo500,
            Blue500,
            Sky500,
            Cyan500,
            Teal500,
            Emerald500,
            Green500,
            Lime500,
            Yellow500,
            Amber500,
            Orange500,
            Stone500
        )

        /**
         * The palette for [themeSeed] — the theme's own key colour.
         *
         * [themeSeed] is the *key* colour harmony pulls towards, not a colour any course is painted
         * with: each course keeps its own hue, chroma and tone, and only its hue is nudged.
         */
        fun from(themeSeed: Color): ClassPalette = ClassPalette(themeSeed)

        /**
         * The palette for [themeSeed] from colours that were generated earlier and kept — see
         * [ClassPaletteCache].
         *
         * Both themes are supplied, so nothing is generated. The seed is still carried, because it is what
         * the palette *is* and what it compares by; a cached palette that was written for another seed
         * never reaches here, which is the cache's own check to make.
         */
        fun of(
            themeSeed: Color,
            light: List<ClassSwatch>,
            dark: List<ClassSwatch>,
        ): ClassPalette = ClassPalette(themeSeed, cachedLight = light, cachedDark = dark)

        /** One seed's four roles, read out of its own harmonized scheme. */
        private fun Color.rolesFor(themeSeedArgb: Int, isDark: Boolean): ClassSwatch {
            val scheme = SchemeTonalSpot(
                sourceColorHct = Hct.fromInt(Blend.harmonize(toArgb(), themeSeedArgb)),
                isDark = isDark,
                contrastLevel = 0.0,
            )
            return ClassSwatch(
                primary = Color(scheme.primary),
                primaryContainer = Color(scheme.primaryContainer),
                onPrimary = Color(scheme.onPrimary),
                onPrimaryContainer = Color(scheme.onPrimaryContainer),
            )
        }
    }
}
