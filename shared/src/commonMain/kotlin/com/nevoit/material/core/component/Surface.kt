package com.nevoit.material.core.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.material.theme.local.LocalContentColor

@Composable
fun Surface(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colors.background,
    contentColor: Color = MaterialTheme.colors.content,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .background(color)
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}
