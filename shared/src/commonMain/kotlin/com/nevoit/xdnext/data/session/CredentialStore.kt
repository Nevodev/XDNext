package com.nevoit.xdnext.data.session

import com.nevoit.xdnext.core.log.appLog
import com.nevoit.xdnext.core.store.SecureKeys
import com.nevoit.xdnext.core.store.SecureStore
import com.nevoit.xdnext.core.store.SettingsKeys
import com.nevoit.xdnext.core.store.SettingsStore
import com.nevoit.xdnext.data.ids.IdsCrypto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the credentials needed to log in without asking the user again.
 *
 * Two different stores on purpose:
 *  - the account and password are secrets and go to [SecureStore] (Android Keystore backed);
 *  - the trusted-device fingerprint is not a secret — it is a random identifier — so it goes to
 *    ordinary settings alongside the other preferences.
 *
 * The original kept the password in plaintext SharedPreferences and the fingerprint in the same
 * place. Splitting them here is a deliberate deviation, and it is the main reason this port cannot
 * reuse the original's preference file.
 */
class CredentialStore(
    private val secureStore: SecureStore,
    private val settings: SettingsStore,
    private val idsCrypto: IdsCrypto,
) {

    /** Serialises get-or-create so two concurrent logins cannot mint two fingerprints. */
    private val fingerprintMutex = Mutex()

    suspend fun account(): String? =
        secureStore.getString(SecureKeys.IDS_ACCOUNT)?.takeIf { it.isNotBlank() }

    suspend fun password(): String? =
        secureStore.getString(SecureKeys.IDS_PASSWORD)?.takeIf { it.isNotBlank() }

    suspend fun hasStoredCredentials(): Boolean = account() != null && password() != null

    /**
     * Whether a login has ever completed on this device, answered **without suspending**.
     *
     * The same question [hasStoredCredentials] asks, asked of the non-secret store: the app has to
     * choose between the login screen and the shell before it draws its first frame, and a keystore
     * read cannot be waited for there. [save] writes the credentials and this flag together and
     * [clear] removes them together, so the two cannot disagree beyond the length of one login.
     */
    fun hasCompletedLogin(): Boolean = settings.getBoolean(SettingsKeys.HAS_COMPLETED_LOGIN)

    suspend fun save(account: String, password: String) {
        // The original saved these only after a *successful* login, so a typo never became the
        // persisted credential. Callers must preserve that ordering.
        secureStore.putString(SecureKeys.IDS_ACCOUNT, account)
        secureStore.putString(SecureKeys.IDS_PASSWORD, password)
        settings.putBoolean(SettingsKeys.HAS_COMPLETED_LOGIN, true)
    }

    /** Forgets the credentials. The cookie jar is cleared separately by the session. */
    suspend fun clear() {
        secureStore.remove(SecureKeys.IDS_ACCOUNT)
        secureStore.remove(SecureKeys.IDS_PASSWORD)
        settings.remove(SettingsKeys.HAS_COMPLETED_LOGIN)
    }

    /**
     * Returns the stored trusted-device fingerprint, creating it on first use.
     *
     * The original validated the stored value against `^[0-9A-F]{32}$` and regenerated it when the
     * check failed, which is why a corrupt or truncated preference self-heals instead of wedging
     * login forever.
     */
    suspend fun browserFingerprint(): String = fingerprintMutex.withLock {
        val stored = settings.getString(SettingsKeys.IDS_BROWSER_FINGERPRINT)
        if (stored != null && FINGERPRINT_PATTERN.matches(stored)) return@withLock stored

        if (stored != null) {
            appLog.w { "Discarding malformed IDS browser fingerprint: ${redactedLength(stored)}" }
        }
        val generated = idsCrypto.generateBrowserFingerprint()
        settings.putString(SettingsKeys.IDS_BROWSER_FINGERPRINT, generated)
        generated
    }

    private fun redactedLength(value: String) = "<${value.length} chars>"

    private companion object {
        val FINGERPRINT_PATTERN = Regex("^[0-9A-F]{32}$")
    }
}
