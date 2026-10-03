package com.nevoit.xdnext.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nevoit.xdnext.data.experiment.ExperimentCredentials
import com.nevoit.xdnext.data.experiment.ExperimentRepository
import com.nevoit.xdnext.data.session.CredentialStore
import com.nevoit.xdnext.ui.symbols.Symbol
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import xdnext.shared.generated.resources.Res
import xdnext.shared.generated.resources.settings_account_settings
import xdnext.shared.generated.resources.settings_experiment_account_default
import xdnext.shared.generated.resources.settings_experiment_account_in_use
import xdnext.shared.generated.resources.settings_experiment_account_unset
import xdnext.shared.generated.resources.settings_experiment_credentials
import xdnext.shared.generated.resources.settings_ids_account
import xdnext.shared.generated.resources.settings_not_logged_in
import xdnext.shared.generated.resources.settings_section_experiment
import xdnext.shared.generated.resources.settings_section_ids

/**
 * 账号设置 — the accounts this app signs in with, and the ones it needs beyond them.
 *
 * The original's 账号设置 was three dialogs in the settings list (体育系统密码设置, 物理实验系统密码设置,
 * 校园网帐号密码设置), each a password field for a system that module then logged into. This page keeps
 * the layout and drops what has no writer: only the physics experiment module exists in this port, so
 * only its row does. A row that stored a password for a system nothing reads would be exactly the kind
 * of inert control the timetable's own strings refuse to carry.
 *
 * The IDS account is shown **read-only**. It is not a setting — the app signed in with it, and changing
 * it means signing in again — but it is the value the lab site's own account defaults to, so the two
 * rows belong on the same page.
 */
@Composable
fun AccountSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val credentials: ExperimentCredentials = koinInject()
    val idsCredentials: CredentialStore = koinInject()
    val experiments: ExperimentRepository = koinInject()
    val scope = rememberCoroutineScope()

    var idsAccount by remember { mutableStateOf<String?>(null) }
    var experimentAccount by remember { mutableStateOf<String?>(null) }
    var experimentAccountIsStored by remember { mutableStateOf(false) }

    var dialogOpen by remember { mutableStateOf(false) }
    var dialogAccount by remember { mutableStateOf("") }
    var dialogPassword by remember { mutableStateOf("") }

    suspend fun readCredentialState() {
        idsAccount = idsCredentials.account()
        experimentAccountIsStored = credentials.storedAccount() != null
        experimentAccount = credentials.account()
    }

    LaunchedEffect(Unit) { readCredentialState() }

    // Read here rather than inside the page's `Section`s: those lambdas only *declare* rows and run
    // outside composition, so a `stringResource` in one would have no composition to read from.
    val idsAccountTitle = stringResource(Res.string.settings_ids_account)
    val notLoggedIn = stringResource(Res.string.settings_not_logged_in)
    val credentialsTitle = stringResource(Res.string.settings_experiment_credentials)
    val accountDetail = when {
        experimentAccount == null -> stringResource(Res.string.settings_experiment_account_unset)
        experimentAccountIsStored -> stringResource(
            Res.string.settings_experiment_account_in_use,
            experimentAccount.orEmpty(),
        )

        else -> stringResource(Res.string.settings_experiment_account_default)
    }

    SettingsPage(
        title = stringResource(Res.string.settings_account_settings),
        onBack = onBack,
        modifier = modifier,
    ) {
        Section(header = { stringResource(Res.string.settings_section_ids) }) {
            Row {
                SettingText(
                    title = idsAccountTitle,
                    detail = idsAccount ?: notLoggedIn,
                )
            }
        }

        Section(header = { stringResource(Res.string.settings_section_experiment) }) {
            SettingRow(
                title = credentialsTitle,
                detail = accountDetail,
                icon = Symbol.Science,
                onClick = {
                    // The dialog is filled from the *stored* values rather than from the effective ones:
                    // an empty account field is how "follow the IDS account" is expressed, so prefilling
                    // the inherited account would turn it into an explicit setting on the first save.
                    scope.launch {
                        dialogAccount = credentials.storedAccount().orEmpty()
                        dialogPassword = credentials.password().orEmpty()
                        dialogOpen = true
                    }
                },
            )
        }
    }

    if (dialogOpen) {
        ExperimentCredentialsDialog(
            account = dialogAccount,
            password = dialogPassword,
            onDismiss = { dialogOpen = false },
            onConfirm = { account, password ->
                dialogOpen = false
                scope.launch {
                    credentials.save(account, password)
                    readCredentialState()
                    // The list on disk was fetched with the old credentials, so it describes an account
                    // that is no longer the one being read: it is dropped and fetched again rather than
                    // kept as a fallback.
                    experiments.onCredentialsChanged()
                }
            },
        )
    }
}
