package com.nevoit.material.core.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.material.theme.local.LocalContentColor
import com.nevoit.material.theme.local.ProvideTextStyle

/**
 * A labelled text field with an outline.
 *
 * The label sits *above* the box rather than floating inside it. That is the whole simplification:
 * no label animation, no placeholder slot, no decoration-box plumbing — one extra line of layout for
 * a field that reads the same in both themes and in an error state.
 *
 * Everything below the label is [BasicTextField] inside a bordered, clipped row, with the trailing
 * icon as a sibling, so the caller keeps full control of the input's options.
 */
@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colors
    val shape = MaterialTheme.specs.textFieldShape
    val borderColor = when {
        !enabled -> colors.content.copy(alpha = 0.12f)
        isError -> colors.error
        else -> colors.inactiveThumb
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            ProvideTextStyle(MaterialTheme.type.footnote) {
                CompositionLocalProvider(
                    LocalContentColor provides if (isError) colors.error else colors.contentVariant,
                    content = label,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, borderColor, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                // Without this the selection highlight is the platform default, which is the one
                // colour in the field that would not come from the palette.
                val selectionColors = remember(colors.primary) {
                    TextSelectionColors(
                        handleColor = colors.primary,
                        backgroundColor = colors.primary.copy(alpha = 0.4f),
                    )
                }
                CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        singleLine = singleLine,
                        textStyle = MaterialTheme.type.body.copy(
                            color = if (enabled) colors.content else colors.contentVariant,
                        ),
                        cursorBrush = SolidColor(colors.primary),
                        visualTransformation = visualTransformation,
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            trailingIcon?.let { it() }
        }
    }
}
