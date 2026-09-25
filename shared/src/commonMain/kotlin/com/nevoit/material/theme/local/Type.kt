package com.nevoit.material.theme.local

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.nevoit.material.theme.MaterialColorsDefault
import com.nevoit.material.theme.MaterialTypeStandard

val LocalContentColor = compositionLocalOf { MaterialColorsDefault.content }

val LocalTextStyle = compositionLocalOf { MaterialTypeStandard.body }

val LocalType = staticCompositionLocalOf { MaterialTypeStandard }

@Composable
fun ProvideTextStyle(
    value: TextStyle,
    content: @Composable () -> Unit
) {
    val mergedStyle = LocalTextStyle.current.merge(value)
    CompositionLocalProvider(
        LocalTextStyle provides mergedStyle,
        content = content
    )
}

@Composable
fun ProvideContentColor(
    value: Color,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalContentColor provides value,
        content = content
    )
}