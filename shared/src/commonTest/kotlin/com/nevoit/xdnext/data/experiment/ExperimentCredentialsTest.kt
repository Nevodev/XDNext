package com.nevoit.xdnext.data.experiment

import com.nevoit.xdnext.core.crypto.AesCbc
import com.nevoit.xdnext.core.store.InMemorySecureStore
import com.nevoit.xdnext.core.store.InMemorySettings
import com.nevoit.xdnext.core.store.SettingsStore
import com.nevoit.xdnext.data.ids.IdsCrypto
import com.nevoit.xdnext.data.session.CredentialStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the lab site's credentials.
 *
 * Two rules are worth pinning, and both are about which account a login actually uses:
 *
 *  - the account **defaults to the one this app signed in with**. That inheritance is what makes the
 *    module work out of the box for the student number that is the same in both systems, and it is the
 *    only behaviour the original had — it sent the IDS account on every experiment login and had no
 *    account setting at all;
 *  - a cleared account goes back to that inheritance rather than becoming an empty string. The settings
 *    dialog expresses "follow IDS" by leaving the field blank, so blank has to *remove* the stored value
 *    — otherwise the first save would silently pin the inherited account and stop following it.
 */
class ExperimentCredentialsTest {

    @Test
    fun theAccountIsTheOneTheAppSignedInWithUntilOneIsSet() = runTest {
        val (credentials, _) = credentials(idsAccount = "20210001")

        assertEquals("20210001", credentials.account())
        assertNull(credentials.storedAccount(), "Nothing of its own is stored yet.")
    }

    @Test
    fun aStoredAccountWinsOverTheIdsOne() = runTest {
        val (credentials, _) = credentials(idsAccount = "20210001")

        credentials.save(account = "lab-user", password = "secret")

        assertEquals("lab-user", credentials.account())
        assertTrue(credentials.storedAccount() != null)
    }

    @Test
    fun aClearedAccountFollowsTheIdsOneAgain() = runTest {
        val (credentials, _) = credentials(idsAccount = "20210001")
        credentials.save(account = "lab-user", password = "secret")

        credentials.save(account = "  ", password = "secret")

        assertNull(credentials.storedAccount())
        assertEquals("20210001", credentials.account())
        assertEquals("secret", credentials.password(), "Clearing the account does not clear the password.")
    }

    @Test
    fun thePasswordStartsAbsentAndLivesOnlyInTheSecureStore() = runTest {
        val (credentials, secureStore) = credentials(idsAccount = "20210001")
        assertNull(credentials.password(), "Until one is set, the module has nothing to log in with.")

        credentials.save(account = "", password = "secret")

        assertEquals("secret", credentials.password())
        assertEquals("secret", secureStore.getString("experiment.password"))
        assertEquals(
            "ids-secret",
            secureStore.getString("ids.password"),
            "The IDS credentials the app signed in with are untouched by the lab site's own.",
        )
    }

    @Test
    fun clearForgetsBoth() = runTest {
        val (credentials, _) = credentials(idsAccount = "20210001")
        credentials.save(account = "lab-user", password = "secret")

        credentials.clear()

        assertNull(credentials.storedAccount())
        assertNull(credentials.password())
    }

    private suspend fun credentials(idsAccount: String): Pair<ExperimentCredentials, InMemorySecureStore> {
        val secureStore = InMemorySecureStore()
        val idsCredentials = CredentialStore(
            secureStore = secureStore,
            settings = SettingsStore(InMemorySettings()),
            idsCrypto = IdsCrypto(AesCbc()),
        )
        idsCredentials.save(account = idsAccount, password = "ids-secret")
        return ExperimentCredentials(secureStore, idsCredentials) to secureStore
    }
}
