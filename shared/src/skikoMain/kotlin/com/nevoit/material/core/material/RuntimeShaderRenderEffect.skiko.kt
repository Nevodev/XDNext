package com.nevoit.material.core.material

import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asSkikoRuntimeShader
import org.jetbrains.skia.ImageFilter

/**
 * Skiko back ends (desktop, iOS, web) run the shader through Skia directly: the
 * `RuntimeShaderBuilder` that `kyant.backdrop` wraps becomes an `ImageFilter`, and Compose wraps
 * that in its own [RenderEffect].
 *
 * Runtime shaders are always available here — `isRuntimeShaderSupported()` returns `true` on Skiko —
 * so this never returns `null`; the nullable signature is the shared one.
 */
internal actual fun runtimeShaderRenderEffect(
    shader: RuntimeShader,
    uniformShaderName: String
): RenderEffect? {
    return ImageFilter.makeRuntimeShader(
        shader.asSkikoRuntimeShader(),
        uniformShaderName,
        null
    ).asComposeRenderEffect()
}
