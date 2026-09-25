package com.nevoit.material.core.utility

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun NavigationBarSpacer() {
    Spacer(modifier = Modifier.navigationBarsPadding())
}

@Composable
fun StatusBarSpacer() {
    Spacer(modifier = Modifier.statusBarsPadding())
}