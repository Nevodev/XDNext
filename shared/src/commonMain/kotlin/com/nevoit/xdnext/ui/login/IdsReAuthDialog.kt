package com.nevoit.xdnext.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nevoit.material.core.component.AlertDialog
import com.nevoit.material.core.component.Checkbox
import com.nevoit.material.core.component.CircularProgressIndicator
import com.nevoit.material.core.component.FilterChip
import com.nevoit.material.core.component.OutlinedButton
import com.nevoit.material.core.component.OutlinedTextField
import com.nevoit.material.core.component.Text
import com.nevoit.material.core.component.TextButton
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.xdnext.data.ids.IdsAuthException
import com.nevoit.xdnext.data.ids.IdsReAuthClient
import com.nevoit.xdnext.data.ids.IdsReAuthCodeType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Collects a second-factor code.
 *
 * Ported from `ids_reauth_dialog.dart`. The dialog owns its own transient state (countdown, notice,
 * inline error) because none of it outlives the dialog; the *challenge* lives in the repository,
 * which is what lets this be dismissed and reopened without losing the server-side session.
 *
 * The dialog is not dismissible by tapping outside, exactly as the original: an ambivalent dismissal
 * would leave the login coroutine parked forever.
 */
@Composable
fun IdsReAuthDialog(
    viewModel: LoginViewModel,
    client: IdsReAuthClient,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    var codeType by remember(client) { mutableStateOf(IdsReAuthCodeType.Sms) }
    var code by remember(client) { mutableStateOf("") }
    var trustDevice by remember(client) { mutableStateOf(false) }
    var notice by remember(client) { mutableStateOf<String?>(null) }
    var error by remember(client) { mutableStateOf<String?>(null) }
    var sending by remember(client) { mutableStateOf(false) }
    var submitting by remember(client) { mutableStateOf(false) }
    var secondsRemaining by remember(client) { mutableIntStateOf(0) }

    // One tick per second while a resend is cooling down. Re-keying on the value is what makes this a
    // countdown rather than a single sleep.
    LaunchedEffect(secondsRemaining) {
        if (secondsRemaining > 0) {
            delay(1_000)
            secondsRemaining -= 1
        }
    }

    val busy = sending || submitting

    fun sendCode() {
        scope.launch {
            sending = true
            error = null
            viewModel.sendReAuthCode(codeType)
                .onSuccess { delivery ->
                    val recipient = delivery.maskedMobile ?: client.recipientDescription
                    notice = if (recipient == null) {
                        delivery.message
                    } else {
                        "${delivery.message}（$recipient）"
                    }
                    secondsRemaining = delivery.retryAfterSeconds
                }
                .onFailure { failure ->
                    error = failure.message ?: "验证码发送失败"
                }
            sending = false
        }
    }

    fun submit() {
        if (code.isBlank()) {
            error = "请输入验证码"
            return
        }
        scope.launch {
            submitting = true
            error = null
            viewModel.submitReAuthCode(codeType, code, trustDevice)
                .onFailure { failure ->
                    // A rejected code is worth retrying, so the field is cleared and the dialog stays.
                    code = ""
                    error = (failure as? IdsAuthException)?.message
                        ?: failure.message
                                ?: "二次认证失败"
                }
            submitting = false
        }
    }

    AlertDialog(
        modifier = modifier,
        // Not dismissible: the login coroutine is parked on this dialog.
        onDismissRequest = { },
        title = { Text("二次验证") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "学校要求本次登录进行二次验证，请选择接收方式并输入验证码。",
                    style = MaterialTheme.type.subHeadline,
                    color = MaterialTheme.colors.contentVariant,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IdsReAuthCodeType.entries.forEach { type ->
                        FilterChip(
                            selected = codeType == type,
                            enabled = !busy,
                            onClick = {
                                if (codeType != type) {
                                    codeType = type
                                    code = ""
                                    notice = null
                                    error = null
                                    secondsRemaining = 0
                                }
                            },
                            label = { Text(type.displayName()) },
                        )
                    }
                }

                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(codeType.displayName()) },
                    singleLine = true,
                    enabled = !busy,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedButton(
                    onClick = { sendCode() },
                    enabled = !busy && secondsRemaining <= 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (secondsRemaining > 0) {
                            "${secondsRemaining} 秒后可重新发送"
                        } else {
                            "发送验证码"
                        },
                    )
                }

                notice?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.type.footnote,
                        color = MaterialTheme.colors.contentVariant,
                    )
                }

                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.type.footnote,
                        color = MaterialTheme.colors.error,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = trustDevice,
                        onCheckedChange = { trustDevice = it },
                        enabled = !busy,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("信任此设备", style = MaterialTheme.type.subHeadline)
                        Text(
                            "下次登录可能不再需要二次验证",
                            style = MaterialTheme.type.footnote,
                            color = MaterialTheme.colors.contentVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = !busy) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("确定")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.cancelReAuth() }, enabled = !busy) {
                Text("取消")
            }
        },
    )
}

private fun IdsReAuthCodeType.displayName(): String = when (this) {
    IdsReAuthCodeType.Sms -> "短信验证码"
    IdsReAuthCodeType.EnterpriseWeChat -> "企业微信"
}
