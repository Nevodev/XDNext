package com.nevoit.xdnext.core.store

/**
 * Key/value storage for secrets that must not sit in plain sight on disk.
 *
 * The original Flutter app stored `idsPassword`, `sportPassword`, `experimentPassword`,
 * `schoolNetQueryPassword` and the Ruisi password as **plaintext** in SharedPreferences. This port
 * treats encrypted storage as a requirement rather than a port, so every credential goes through
 * this interface.
 *
 * Implementations must never log the value, and must tolerate a missing key by returning null.
 */
interface SecureStore {

    suspend fun putString(key: String, value: String)

    suspend fun getString(key: String): String?

    suspend fun remove(key: String)

    /** Removes everything this store owns. Used by log-out and "clear all data". */
    suspend fun clear()
}

/** Keys used with [SecureStore]. Kept in one place so a typo cannot silently orphan a secret. */
object SecureKeys {
    const val IDS_ACCOUNT = "ids.account"
    const val IDS_PASSWORD = "ids.password"

    /**
     * The physics experiment system's own credentials.
     *
     * A separate account from [IDS_ACCOUNT] because the lab site is not behind IDS: it has a login form
     * of its own, and the student number that works there happens to also be the IDS one. The original
     * sent the IDS account for every experiment login and never stored one of its own; the account is
     * stored here so it can be changed without changing the account this app signs into.
     */
    const val EXPERIMENT_ACCOUNT = "experiment.account"
    const val EXPERIMENT_PASSWORD = "experiment.password"
}

/**
 * In-memory [SecureStore] for unit tests and for platforms that have no keystore.
 *
 * Deliberately not persisted: a test that needs persistence should assert against a fake instead of
 * inheriting an accidental disk format.
 */
class InMemorySecureStore : SecureStore {
    private val values = mutableMapOf<String, String>()

    override suspend fun putString(key: String, value: String) {
        values[key] = value
    }

    override suspend fun getString(key: String): String? = values[key]

    override suspend fun remove(key: String) {
        values.remove(key)
    }

    override suspend fun clear() {
        values.clear()
    }
}
