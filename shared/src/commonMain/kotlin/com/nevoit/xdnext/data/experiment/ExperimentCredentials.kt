package com.nevoit.xdnext.data.experiment

import com.nevoit.xdnext.core.store.SecureKeys
import com.nevoit.xdnext.core.store.SecureStore
import com.nevoit.xdnext.data.session.CredentialStore

/**
 * The lab site's own credentials, kept apart from the ones this app signs in with.
 *
 * The original had no account of its own for the experiment system: it sent the IDS student number with
 * a *separate* password (`experimentPassword`, stored in plaintext SharedPreferences), and the settings
 * dialog that set it was titled 修改物理实验账号密码 without touching the account at all. Both facts are
 * reproduced with one change — the account is a stored value too, because the settings page this port
 * builds offers to set it, and a stored account that cannot be set is a field with no writer.
 *
 * The IDS account is therefore the *default*: [account] answers the stored one when there is one and
 * what the app signed in with otherwise, which is what makes the module work out of the box for the
 * student number that is the same in both systems.
 *
 * The password is a secret and lives in [SecureStore] — the original kept it in plaintext, which is one
 * of the deviations this port makes deliberately (see the README).
 */
class ExperimentCredentials(
    private val secureStore: SecureStore,
    private val idsCredentials: CredentialStore,
) {

    /** The account the lab site is logged into with: the stored one, or the IDS one. */
    suspend fun account(): String? =
        storedAccount() ?: idsCredentials.account()

    /** The account the user set for the lab site alone, or null when it follows the IDS one. */
    suspend fun storedAccount(): String? =
        secureStore.getString(SecureKeys.EXPERIMENT_ACCOUNT)?.takeIf { it.isNotBlank() }

    /** The lab site's password, or null when the user has never set one. */
    suspend fun password(): String? =
        secureStore.getString(SecureKeys.EXPERIMENT_PASSWORD)?.takeIf { it.isNotBlank() }

    /**
     * Saves the credentials after a successful login or from the settings page.
     *
     * Blank values are stored as *absent* rather than as empty strings, so "the user cleared the account
     * to follow IDS again" and "the user has never set one" are the same state — which is what the
     * settings row shows, and what makes clearing a field feel like clearing it.
     */
    suspend fun save(account: String, password: String) {
        val trimmedAccount = account.trim()
        if (trimmedAccount.isEmpty()) {
            secureStore.remove(SecureKeys.EXPERIMENT_ACCOUNT)
        } else {
            secureStore.putString(SecureKeys.EXPERIMENT_ACCOUNT, trimmedAccount)
        }
        if (password.isEmpty()) {
            secureStore.remove(SecureKeys.EXPERIMENT_PASSWORD)
        } else {
            secureStore.putString(SecureKeys.EXPERIMENT_PASSWORD, password)
        }
    }

    /** Forgets both. The cache goes with them — see [ExperimentRepository.refresh]. */
    suspend fun clear() {
        secureStore.remove(SecureKeys.EXPERIMENT_ACCOUNT)
        secureStore.remove(SecureKeys.EXPERIMENT_PASSWORD)
    }
}
