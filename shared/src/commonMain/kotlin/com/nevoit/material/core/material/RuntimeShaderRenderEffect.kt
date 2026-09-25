package com.nevoit.material.core.material

import androidx.compose.ui.graphics.RenderEffect
import com.kyant.backdrop.RuntimeShader

internal expect fun runtimeShaderRenderEffect(
    shader: RuntimeShader,
    uniformShaderName: String
): RenderEffect?
