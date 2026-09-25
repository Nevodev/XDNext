package com.nevoit.xdnext.ui.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nevoit.material.core.component.IconButton
import com.nevoit.material.core.component.TopBar
import com.nevoit.xdnext.ui.symbols.Symbol

@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    TopBar(
        title = title,
        modifier = modifier,
        leading = {
            IconButton(
                symbol = Symbol.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
            )
        },
        trailing = trailing
    )
}