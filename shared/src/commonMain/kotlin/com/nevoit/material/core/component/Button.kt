package com.nevoit.material.core.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.nevoit.material.core.modifier.thenIf
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.material.theme.local.LocalContentColor
import com.nevoit.material.theme.local.ProvideTextStyle
import com.nevoit.xdnext.ui.symbols.SymbolIcon

/**
 * The button family, in the one shape a button actually varies by: which three colours it uses.
 *
 * Everything else — the shape, the height, the padding, the content colour, the label style, the
 * disabled treatment — is shared by [ButtonSurface], so the three variants cannot drift apart. They
 * are deliberately thin: no elevation, no icon slot, no content padding parameter, because nothing
 * here needs one yet.
 */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colors
    ButtonSurface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.specs.buttonShape,
        background = if (enabled) colors.primary else colors.primary.copy(alpha = 0.12f),
        borderColor = null,
        contentColor = if (enabled) colors.onPrimary else colors.onPrimary.copy(alpha = 0.38f),
        content = content,
    )
}

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colors
    ButtonSurface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.specs.buttonShape,
        background = Color.Transparent,
        // The palette has exactly one outline-role colour, and this is it.
        borderColor = colors.inactiveThumb,
        contentColor = if (enabled) colors.primary else colors.content.copy(alpha = 0.38f),
        content = content,
    )
}

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colors
    ButtonSurface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.specs.buttonShape,
        background = Color.Transparent,
        borderColor = null,
        contentColor = if (enabled) colors.primary else colors.content.copy(alpha = 0.38f),
        content = content,
    )
}

/**
 * A square tap target for a lone icon.
 *
 * The icon tint comes from [LocalContentColor], so the caller passes a plain `SymbolIcon` with no
 * colour of its own; the muted variant role is what a Material icon button uses.
 */
@Composable
fun IconButton(
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyles.secondaryVariant(),
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .thenIf(!enabled) {
                graphicsLayer {
                    this.alpha = style.disabledAlpha
                }
            }
            .size(48.dp)
            .clip(style.containerShape)
            .background(style.containerColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides style.contentColor,
            content = content
        )
    }
}

@Composable
fun IconButton(
    symbol: String,
    contentDescription: String? = null,
    style: ButtonStyle = ButtonStyles.secondaryVariant(),
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = modifier, style = style) {
        SymbolIcon(
            name = symbol,
            contentDescription = contentDescription,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun ButtonSurface(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    background: Color,
    borderColor: Color?,
    contentColor: Color,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(shape)
            .background(background)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(MaterialTheme.type.subHeadlineEmphasized) {
                content()
            }
        }
    }
}

@Immutable
data class ButtonStyle(
    val containerShape: Shape = CircleShape,
    val containerColor: Color,
    val contentColor: Color,
    val disabledAlpha: Float = 0.5f,
)

object ButtonStyles {

    @Composable
    fun primary(
        containerShape: Shape = CircleShape,
        containerColor: Color = MaterialTheme.colors.primary,
        contentColor: Color = MaterialTheme.colors.onPrimary,
        disabledAlpha: Float = 0.5f
    ): ButtonStyle = ButtonStyle(
        containerShape = containerShape,
        containerColor = containerColor,
        contentColor = contentColor,
        disabledAlpha = disabledAlpha
    )

    @Composable
    fun primaryVariant(
        containerShape: Shape = CircleShape,
        containerColor: Color = MaterialTheme.colors.primaryContainer,
        contentColor: Color = MaterialTheme.colors.onPrimaryContainer,
        disabledAlpha: Float = 0.5f
    ): ButtonStyle = ButtonStyle(
        containerShape = containerShape,
        containerColor = containerColor,
        contentColor = contentColor,
        disabledAlpha = disabledAlpha
    )

    @Composable
    fun secondary(
        containerShape: Shape = CircleShape,
        containerColor: Color = MaterialTheme.colors.secondary,
        contentColor: Color = MaterialTheme.colors.onSecondary,
        disabledAlpha: Float = 0.5f
    ): ButtonStyle = ButtonStyle(
        containerShape = containerShape,
        containerColor = containerColor,
        contentColor = contentColor,
        disabledAlpha = disabledAlpha
    )

    @Composable
    fun secondaryVariant(
        containerShape: Shape = CircleShape,
        containerColor: Color = MaterialTheme.colors.secondaryContainer,
        contentColor: Color = MaterialTheme.colors.onSecondaryContainer,
        disabledAlpha: Float = 0.5f
    ): ButtonStyle = ButtonStyle(
        containerShape = containerShape,
        containerColor = containerColor,
        contentColor = contentColor,
        disabledAlpha = disabledAlpha
    )

    @Composable
    fun tertiary(
        containerShape: Shape = CircleShape,
        containerColor: Color = MaterialTheme.colors.tertiary,
        contentColor: Color = MaterialTheme.colors.onTertiary,
        disabledAlpha: Float = 0.5f
    ): ButtonStyle = ButtonStyle(
        containerShape = containerShape,
        containerColor = containerColor,
        contentColor = contentColor,
        disabledAlpha = disabledAlpha
    )

    @Composable
    fun tertiaryVariant(
        containerShape: Shape = CircleShape,
        containerColor: Color = MaterialTheme.colors.tertiaryContainer,
        contentColor: Color = MaterialTheme.colors.onTertiaryContainer,
        disabledAlpha: Float = 0.5f
    ): ButtonStyle = ButtonStyle(
        containerShape = containerShape,
        containerColor = containerColor,
        contentColor = contentColor,
        disabledAlpha = disabledAlpha
    )

    @Composable
    fun error(
        containerShape: Shape = CircleShape,
        containerColor: Color = MaterialTheme.colors.error,
        contentColor: Color = MaterialTheme.colors.onError,
        disabledAlpha: Float = 0.5f
    ): ButtonStyle = ButtonStyle(
        containerShape = containerShape,
        containerColor = containerColor,
        contentColor = contentColor,
        disabledAlpha = disabledAlpha
    )
}