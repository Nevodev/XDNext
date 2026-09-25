package com.nevoit.material.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.nevoit.material.core.interaction.DimIndication
import com.nevoit.material.core.interaction.overscroll.rememberOffsetOverscrollFactory
import com.nevoit.material.theme.local.LocalColors
import com.nevoit.material.theme.local.LocalContentColor
import com.nevoit.material.theme.local.LocalDarkTheme
import com.nevoit.material.theme.local.LocalSpecs
import com.nevoit.material.theme.local.LocalTextStyle
import com.nevoit.material.theme.local.LocalType

object MaterialTheme {
    val colors: MaterialColors
        @Composable get() = LocalColors.current

    val specs: MaterialSpecs
        @Composable get() = LocalSpecs.current

    val type: MaterialType
        @Composable get() = LocalType.current

    val darkTheme: Boolean
        @Composable get() = LocalDarkTheme.current

    /**
     * Applies the theme: one seed colour in, the whole palette out.
     *
     * The palette is generated from [seedColor] — by default the system accent, see
     * [rememberSystemSeedColor] — so the app follows the device theme rather than shipping colours of
     * its own. Everything downstream reads it through [colors], including the components in
     * `com.nevoit.material.core.component`. Passing [colors] explicitly overrides the generator,
     * which keeps a preview or a test able to pin colours without replacing it.
     */
    @Composable
    operator fun invoke(
        darkTheme: Boolean = resolveDarkTheme(ThemeMode.SYSTEM),
        seedColor: Color = rememberSystemSeedColor(),
        colors: MaterialColors = remember(seedColor, darkTheme) {
            materialColorsFromSeed(seedColor, darkTheme)
        },
        specs: MaterialSpecs = MaterialSpecsStandard,
        type: MaterialType = MaterialTypeStandard,
        content: @Composable () -> Unit
    ) {
        val overscrollFactory = rememberOffsetOverscrollFactory()
        CompositionLocalProvider(
            LocalColors provides colors,
            LocalSpecs provides specs,
            LocalType provides type,
            LocalTextStyle provides type.body,
            LocalContentColor provides colors.content,
            LocalDarkTheme provides darkTheme,
            LocalIndication provides DimIndication(),
            LocalOverscrollFactory provides overscrollFactory
        ) {
            content()
        }
    }
}
