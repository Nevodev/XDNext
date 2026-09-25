package com.nevoit.material.core.material

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.RenderEffect
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.isRuntimeShaderSupported

@Composable
fun rememberMaterialRenderEffectOrNull(recipe: MaterialRecipe): RenderEffect? {
    return if (isRuntimeShaderSupported()) {
        rememberMaterialRenderEffect(recipe)
    } else {
        null
    }
}

@Composable
fun rememberMaterialRenderEffect(recipe: MaterialRecipe): RenderEffect {
    return remember(recipe) {
        recipe.toRenderEffect()
    }
}

fun MaterialRecipe.toRenderEffect(): RenderEffect {
    return toRenderEffectOrNull()
        ?: error("Runtime shaders are not supported on this platform; use toRenderEffectOrNull().")
}

fun MaterialRecipe.toRenderEffectOrNull(): RenderEffect? {
    if (!isRuntimeShaderSupported()) return null

    val shader = RuntimeShader(AGSL_CODE)

    shader.setFloatUniform("p0", luminanceMapCurve.p0)
    shader.setFloatUniform("p1", luminanceMapCurve.p1)
    shader.setFloatUniform("p2", luminanceMapCurve.p2)
    shader.setFloatUniform("p3", luminanceMapCurve.p3)
    shader.setFloatUniform("mapIntensity", luminanceMapIntensity)
    shader.setFloatUniform("saturation", saturation)
    shader.setFloatUniform("brightness", extraBrightness)
    shader.setFloatUniform("ditherStrength", 1.0f)

    return runtimeShaderRenderEffect(shader, "image")
}
