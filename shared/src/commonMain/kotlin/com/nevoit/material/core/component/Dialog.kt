package com.nevoit.material.core.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.material.theme.local.LocalContentColor
import com.nevoit.material.theme.local.ProvideTextStyle

/**
 * A modal dialog: a title, some content, and the two actions along the bottom edge.
 *
 * The slots are lambdas rather than strings and data, so a caller can put anything in the body — the
 * second-factor dialog fills it with a code picker, a field and a resend button — while the frame,
 * the padding and the ordering of the actions stay in one place.
 *
 * [onDismissRequest] is called for a back press or a tap outside; a caller that must not be
 * dismissed passes an empty lambda, which is how the login dialogs park the login coroutine safely.
 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: @Composable () -> Unit = {},
    dismissButton: (@Composable () -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = modifier.widthIn(min = 280.dp, max = 560.dp),
            color = MaterialTheme.colors.elevatedCardBackground
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (title != null || text != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        title?.let { titleContent ->
                            CompositionLocalProvider(
                                LocalContentColor provides MaterialTheme.colors.content
                            ) {
                                ProvideTextStyle(MaterialTheme.type.title3Emphasized) {
                                    titleContent()
                                }
                            }
                        }
                        text?.let { it() }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}
