package com.nevoit.material.core.modifier

import androidx.compose.ui.Modifier

inline fun Modifier.thenIf(precondition: Boolean, action: Modifier.() -> Modifier): Modifier =
    if (precondition) action() else this