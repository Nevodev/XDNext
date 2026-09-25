package com.nevoit.xdnext.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.Font
import xdnext.shared.generated.resources.Res
import xdnext.shared.generated.resources.google_sans_flex_w100_r100_500

/**
 * The face the campus cards' numbers are set in.
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
