package com.nevoit.xdnext.data.session

import com.nevoit.xdnext.core.crypto.AesCbc
import com.nevoit.xdnext.core.store.InMemorySecureStore
import com.nevoit.xdnext.core.store.InMemorySettings
import com.nevoit.xdnext.core.store.SettingsKeys
import com.nevoit.xdnext.core.store.SettingsStore
import com.nevoit.xdnext.data.ids.IdsAuthApi
import com.nevoit.xdnext.data.ids.IdsCrypto
import com.nevoit.xdnext.data.ids.IdsLoginPhase
import com.nevoit.xdnext.data.net.FileCookieStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.FileSystem
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the automatic-login state machine's one interaction with the *choice of screen*.
 *
 * The login screen and the shell are chosen before the first frame from a preference flag, because the
 * credentials themselves live behind a keystore read that cannot be waited for there. That makes the flag
 * a promise about the secure store, and these tests pin the case where the store cannot keep it: the flag
 * must go, or every launch composes the shell and then falls back to the login screen.
 */
class IdsSessionRepositoryTest {

    @Test
    fun aLoginFlagWithNothingBehindItIsDropped() = runTest {
        val settings = SettingsStore(
            InMemorySettings(mapOf(SettingsKeys.HAS_COMPLETED_LOGIN to true)),
        )
        val credentials = CredentialStore(InMemorySecureStore(), settings, IdsCrypto(AesCbc()))
        val session = session(credentials)

        assertTrue(credentials.hasCompletedLogin(), "The flag decides which screen opens.")

        val result = session.restoreSession()

        assertTrue(result.isFailure, "There is nothing to log in with.")
        assertEquals(IdsLoginPhase.NeedsCredentials, session.status.value.phase)
        assertFalse(
            credentials.hasCompletedLogin(),
            "A stale flag would open the shell and then fall back to the login screen on every launch.",
        )
    }

    @Test
    fun aLoginFlagWithCredentialsBehindItIsLeftAlone() = runTest {
        // The same call must not touch a flag that is telling the truth: `restoreSession` here fails at
        // the network, which is the ordinary off-campus case, and the credentials survive it.
        val settings = SettingsStore(InMemorySettings())
        val credentials = CredentialStore(InMemorySecureStore(), settings, IdsCrypto(AesCbc()))
        credentials.save(account = "20210001", password = "secret")

        val session = session(credentials)
        session.restoreSession()

        assertTrue(credentials.hasCompletedLogin())
        assertTrue(credentials.hasStoredCredentials())
    }

    private fun session(credentials: CredentialStore): IdsSessionRepository {
        val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "xdnext-ids-session-test-${instance++}"
        val cookies = FileCookieStorage(FileSystem.SYSTEM, directory / "ids.json", JSON)
        return IdsSessionRepository(
            // No request is expected to succeed in either test: one returns before it makes one, and the
            // other fails at the transport layer, which is the ordinary off-campus case.
            api = IdsAuthApi(HttpClient(MockEngine { throw IOException("offline") }), cookies, JSON),
            idsCrypto = IdsCrypto(AesCbc()),
            credentials = credentials,
        )
    }

    private companion object {
        val JSON = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

        /** Distinguishes the fixtures' cookie files, so no two tests share one. */
        var instance = 0
    }
}
