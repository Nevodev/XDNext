package com.nevoit.xdnext.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nevoit.material.core.component.AlertDialog
import com.nevoit.material.core.component.IconButton
import com.nevoit.material.core.component.OutlinedTextField
import com.nevoit.material.core.component.Text
import com.nevoit.material.core.component.TextButton
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.xdnext.ui.symbols.Symbol
import org.jetbrains.compose.resources.stringResource
import xdnext.shared.generated.resources.Res
import xdnext.shared.generated.resources.settings_account_hint
import xdnext.shared.generated.resources.settings_account_label
import xdnext.shared.generated.resources.settings_blank_input
import xdnext.shared.generated.resources.settings_cancel
import xdnext.shared.generated.resources.settings_confirm
import xdnext.shared.generated.resources.settings_experiment_credentials
import xdnext.shared.generated.resources.settings_hide_password
import xdnext.shared.generated.resources.settings_password_label
import xdnext.shared.generated.resources.settings_show_password

/**
 * 修改物理实验账号密码 — the lab site's credentials.
 *
 * Ported from `experiment_password_dialog.dart`, which asked for the **password** only: the account was
 * always the IDS student number, so it had no field. This one has both, because the page it opens from
 * offers to set the account too — and because the two are one login: a user whose lab account is not
 * their student number has no way to say so otherwise.
 *
 * The original's three behaviours are kept:
 *
 *  - the stored password is **prefilled**, so the dialog is a confirmation rather than a retyping;
 *  - it is **obscured**, with the eye that reveals it (`Icons.visibility` there, [Symbol.Visibility]
 *    here);
 *  - an empty password is **refused** with its own words (`setting.change_password_dialog.blank_input`,
 *    输入空白!), rather than saved as "no password" — which would silently break the module, since a
 *    blank password means the page can fetch nothing.
 *
 * The account, unlike the password, may be left blank: blank is the documented "follow the IDS account"
 * state, which is what the lab site expects for most students anyway.
 */
@Composable
internal fun ExperimentCredentialsDialog(
    account: String,
    password: String,
    onDismiss: () -> Unit,
    onConfirm: (account: String, password: String) -> Unit,
) {
    var enteredAccount by remember { mutableStateOf(account) }
    var enteredPassword by remember { mutableStateOf(password) }
    var passwordVisible by remember { mutableStateOf(false) }
    var blankPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_experiment_credentials)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = enteredAccount,
                    onValueChange = { enteredAccount = it },
                    label = { Text(stringResource(Res.string.settings_account_label)) },
                )
                Text(
                    text = stringResource(Res.string.settings_account_hint),
                    style = MaterialTheme.type.footnote,
                    color = MaterialTheme.colors.contentVariant,
                )

                OutlinedTextField(
                    value = enteredPassword,
                    onValueChange = {
                        enteredPassword = it
                        blankPassword = false
                    },
                    label = { Text(stringResource(Res.string.settings_password_label)) },
                    isError = blankPassword,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(
                            symbol = if (passwordVisible) {
                                Symbol.VisibilityOff
                            } else {
                                Symbol.Visibility
                            },
                            contentDescription = stringResource(
                                if (passwordVisible) {
                                    Res.string.settings_hide_password
                                } else {
                                    Res.string.settings_show_password
                                },
                            ),
                            onClick = { passwordVisible = !passwordVisible },
                        )
                    },
                )
                if (blankPassword) {
                    Text(
                        text = stringResource(Res.string.settings_blank_input),
                        style = MaterialTheme.type.footnote,
                        color = MaterialTheme.colors.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (enteredPassword.isBlank()) {
                        blankPassword = true
                    } else {
                        onConfirm(enteredAccount, enteredPassword)
                    }
                },
            ) {
                Text(stringResource(Res.string.settings_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.settings_cancel))
            }
        },
    )
}
