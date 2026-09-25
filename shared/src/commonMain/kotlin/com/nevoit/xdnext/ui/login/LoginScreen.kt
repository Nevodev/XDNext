package com.nevoit.xdnext.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nevoit.material.core.component.Button
import com.nevoit.material.core.component.IconButton
import com.nevoit.material.core.component.LinearProgressIndicator
import com.nevoit.material.core.component.OutlinedButton
import com.nevoit.material.core.component.OutlinedTextField
import com.nevoit.material.core.component.Text
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.xdnext.data.ids.IdsLoginPhase
import com.nevoit.xdnext.data.ids.IdsLoginStatus
import com.nevoit.xdnext.ui.symbols.Symbol
import com.nevoit.xdnext.ui.symbols.SymbolIcon

/**
 * The login screen.
 *
 * Stateless apart from the injected [LoginViewModel]: every value it renders arrives as a `StateFlow`
 * and every interaction is a method call, so the screen can be previewed with fabricated statuses.
 */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    modifier: Modifier = Modifier,
) {
    val status by viewModel.status.collectAsState()
    val account by viewModel.account.collectAsState()
    val password by viewModel.password.collectAsState()
    val passwordVisible by viewModel.passwordVisible.collectAsState()

    val isError =
        status.phase == IdsLoginPhase.Failed || status.phase == IdsLoginPhase.PasswordWrong
    val canSubmit = !status.isBusy && account.isNotBlank() && password.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "XDNext",
            style = MaterialTheme.type.largeTitle,
        )
        Text(
            text = "西电学生信息助手",
            style = MaterialTheme.type.subHeadline,
            color = MaterialTheme.colors.contentVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 40.dp),
        )

        Column(
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = account,
                onValueChange = viewModel::onAccountChange,
                label = { Text("学号 / 工号") },
                singleLine = true,
                enabled = !status.isBusy,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("密码") },
                singleLine = true,
                enabled = !status.isBusy,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (canSubmit) viewModel.submit() }),
                trailingIcon = {
                    IconButton(
                        onClick = viewModel::togglePasswordVisibility,
                        enabled = !status.isBusy,
                    ) {
                        SymbolIcon(
                            name = if (passwordVisible) Symbol.VisibilityOff else Symbol.Visibility,
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (status.isBusy) {
                StatusBlock(status)
            } else {
                status.message?.let { message ->
                    Text(
                        text = message,
                        color = if (isError) {
                            MaterialTheme.colors.error
                        } else {
                            MaterialTheme.colors.contentVariant
                        },
                        style = MaterialTheme.type.subHeadline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Button(
                onClick = viewModel::submit,
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("登录")
            }

            if (status.phase == IdsLoginPhase.Success) {
                OutlinedButton(
                    onClick = viewModel::logout,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("退出登录")
                }
            }
        }

        Text(
            text = "登录即表示你使用学校统一认证账号。" +
                    "账号密码仅加密保存在本机，用于自动登录。",
            style = MaterialTheme.type.footnote,
            color = MaterialTheme.colors.contentVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 40.dp).widthIn(max = 420.dp),
        )
    }
}

@Composable
private fun StatusBlock(status: IdsLoginStatus) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Determinate progress mirrors the percentage the original fed to its progress dialog.
        LinearProgressIndicator(
            progress = { status.progress.coerceIn(0, 100) / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
        if (status.stepLabel.isNotEmpty()) {
            Text(
                text = status.stepLabel,
                style = MaterialTheme.type.footnote,
                color = MaterialTheme.colors.contentVariant,
            )
        }
    }
}
