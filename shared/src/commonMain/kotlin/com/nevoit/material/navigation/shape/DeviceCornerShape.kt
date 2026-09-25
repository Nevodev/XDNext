package com.nevoit.material.navigation.shape

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class CornerRadii(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomStart: Dp,
    val bottomEnd: Dp,
)

@Composable
expect fun deviceCornerShape(
    padding: Dp = 0.dp,
    topLeft: Boolean = true,
    topRight: Boolean = true,
    bottomRight: Boolean = true,
    bottomLeft: Boolean = true,
): Shape

@Composable
expect fun deviceCornerRadii(): CornerRadii
