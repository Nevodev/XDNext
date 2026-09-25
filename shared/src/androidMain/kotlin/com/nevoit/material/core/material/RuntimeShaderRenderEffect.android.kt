package com.nevoit.material.core.material

import android.os.Build
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asAndroidRuntimeShader

/**
 * Android runs the shader on `android.graphics.RuntimeShader`, which the platform only exposes from
 * API 33 (Tiramisu); `RuntimeShader` from `kyant.backdrop` is a thin wrapper over it.
 *
 * The version check comes first and returns `null`, so a device on API 29–32 shows the plain surface
 * instead of dying on a missing class. Keeping the check inside the actual — rather than annotating
 * the shared declaration with `@RequiresApi` — is what lets the common code call this unconditionally.
 */
internal actual fun runtimeShaderRenderEffect(
    shader: RuntimeShader,
    uniformShaderName: String
): RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null

    return android.graphics.RenderEffect
        .createRuntimeShaderEffect(shader.asAndroidRuntimeShader(), uniformShaderName)
        .asComposeRenderEffect()
}
