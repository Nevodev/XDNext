package com.nevoit.xdnext.ui.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import com.nevoit.material.theme.MaterialTheme
import org.jetbrains.compose.resources.Font
import xdnext.shared.generated.resources.Res
import xdnext.shared.generated.resources.google_sans_flex_w100_r100_500

/**
 * How the app's cards set their numbers: the face they are drawn in, and the number-plus-unit text
 * that is built with it.
 *
 * Shared rather than owned by a screen, because the numbers are not one screen's: the campus cards,
 * the timetable and the electricity page all set a value this way, and a card that leads a page with a
 * number ([SummaryCard]) is the newest of them. The two go together — the unit has to be set inside the
 * value's own text *and* in the value's own face — so they are kept in the same file.
 */

/**
 * The face the cards' numbers are set in.
 *
 * **Google Sans Flex**, instanced to one static cut rather than shipped as the variable font: the file
 * in `composeResources/font` was generated from
 * `GoogleSansFlex-VariableFont_GRAD,ROND,opsz,slnt,wdth,wght.ttf` with
 *
 * ```
 * fonttools varLib.instancer <var> wdth=100 wght=500 GRAD=0 ROND=100 opsz=20 slnt=0 --static
 * ```
 *
 * which is the family's own width at medium weight, fully rounded (`ROND` 100) and at a 20pt optical
 * size. Shipping the variable font instead would cost 4 MB for the same result, and the static instance
 * is 132 KB.
 *
 * It is applied per call site, not to the theme's `title3`: that style is also the slider captcha's
 * heading, and a face chosen for the card numbers should not silently restyle a login dialog.
 *
 * The font has no CJK coverage, so the Chinese in a value falls through to the system face; only the
 * Latin and the digits change. That is the intended look — the numbers are what this face is for.
 */
@Composable
internal fun rememberCardValueFontFamily(): FontFamily {
    val cardValue = Font(Res.font.google_sans_flex_w100_r100_500)
    return remember(cardValue) { FontFamily(cardValue) }
}

/**
 * A card's number with its unit set inside the same text.
 *
 * One [AnnotatedString] rather than two `Text`s, which is the point: a unit in a text box of its own is
 * laid out on its own baseline with its own font fallback, and HyperOS is where that shows up as the
 * number and its unit sitting at different heights. The unit is dimmed rather than shrunk so the pair
 * still reads as one figure.
 *
 * [style] is the card's own value style — the electricity page's headline number and the campus cards'
 * `title2` are this same construction at two sizes — and the value always takes
 * [rememberCardValueFontFamily], whatever style it is drawn in.
 */
@Composable
internal fun rememberValueWithUnit(
    value: String,
    unit: String?,
    style: TextStyle,
    unitColor: Color = MaterialTheme.colors.content.copy(alpha = 0.25f),
): AnnotatedString {
    val valueStyle = style.copy(fontFamily = rememberCardValueFontFamily()).toSpanStyle()
    val unitStyle = style.toSpanStyle().copy(color = unitColor)
    return remember(value, unit, valueStyle, unitStyle) {
        AnnotatedString.Builder().apply {
            withStyle(valueStyle) { append(value) }
            unit?.let { withStyle(unitStyle) { append("\u2006$it") } } // 1/6 rem space
        }.toAnnotatedString()
    }
}
