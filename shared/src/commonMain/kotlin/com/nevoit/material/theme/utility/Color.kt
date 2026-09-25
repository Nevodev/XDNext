package com.nevoit.material.theme.utility

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class OklchColor(
    val l: Float,
    val c: Float,
    val h: Float,
    val alpha: Float = 1f
) {
    fun toColor(): Color {
        val hRad = h * PI / 180.0

        val a = (c * cos(hRad)).toFloat()
        val b = (c * sin(hRad)).toFloat()

        return Color(
            colorSpace = ColorSpaces.Oklab,
            red = l,
            green = a,
            blue = b,
            alpha = alpha
        ).convert(ColorSpaces.Srgb)
    }
}

fun Color.toOklch(): OklchColor {
    val oklab = this.convert(ColorSpaces.Oklab)

    val l = oklab.component1()
    val a = oklab.component2()
    val b = oklab.component3()
    val alpha = oklab.alpha

    val c = hypot(a.toDouble(), b.toDouble()).toFloat()

    var h = (atan2(b.toDouble(), a.toDouble()) * 180.0 / PI).toFloat()
    if (h < 0f) {
        h += 360f
    }

    return OklchColor(l, c, h, alpha)
}

fun Color.purify(factor: Float = 1f): Color {
    val oklch = this.toOklch()

    val purifiedL = if (oklch.l == 1f) 1f else (oklch.l - 0.75f) * (1f - factor) + 0.75f
    val purifiedC = (oklch.c - 0.164f) * (1f - factor) + 0.164f

    return OklchColor(purifiedL, purifiedC, oklch.h, oklch.alpha).toColor()
}

fun Color.umamify(factor: Float = 1f): Color {
    val oklch = this.toOklch()

    return OklchColor(oklch.l, oklch.c * factor, oklch.h, oklch.alpha).toColor()
}

fun Color.lumify(factor: Float = 1f): Color {
    val oklch = this.toOklch()

    return OklchColor((oklch.l * factor).coerceIn(0f, 1f), oklch.c, oklch.h, oklch.alpha).toColor()
}