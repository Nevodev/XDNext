package com.nevoit.xdnext.ui.login

import com.nevoit.xdnext.core.crypto.AesCbc
import com.nevoit.xdnext.core.store.InMemorySecureStore
import com.nevoit.xdnext.core.store.InMemorySettings
import com.nevoit.xdnext.core.store.SettingsStore
import com.nevoit.xdnext.data.ids.IdsCrypto
import com.nevoit.xdnext.data.ids.IdsLoginPhase
import com.nevoit.xdnext.data.ids.IdsLoginStatus
import com.nevoit.xdnext.data.session.CredentialStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the gate between the login screen and the shell.
 *
 * The gate is driven here through a **real [CredentialStore]** rather than a stubbed boolean, because
 * the property that broke in practice is not the rule but its *input*: the flag on disk changes when a
 * login succeeds, and a gate that froze it at start-up sent the user back to the login form a frame
 * after the shell appeared. The test that pins that — [aRefreshingShellStaysOnScreen] — is the reason
 * this class exists.
 */
class LoginGateTest {

    private val credentials = credentialStore()
    private val gate = LoginGate(credentials::hasCompletedLogin)

    @Test
    fun aDeviceThatHasNeverLoggedInOpensOnTheLoginScreen() {
        assertFalse(gate.loggedIn.value)
    }

    @Test
    fun aDeviceThatHasLoggedInBeforeOpensOnTheShell() = runBlocking {
        // Nothing has been attempted in this process; the answer comes from disk, synchronously, which
        // is what lets the first frame be the right screen instead of a screen that navigates.
        credentials.save(ACCOUNT, PASSWORD)

        assertTrue(LoginGate(credentials::hasCompletedLogin).loggedIn.value)
    }

    @Test
    fun aCompletedLoginMovesTheUserToTheShell() {
        gate.onStatus(IdsLoginStatus.success())

        assertTrue(gate.loggedIn.value)
    }

    @Test
    fun aRefreshingShellStaysOnScreen() = runBlocking {
        // The regression. The shell composes, its card refresh logs in for its own service, and that
        // reports `RestoringSession` — not `Success`. Read as "this device has never logged in", the
        // shell is torn down and the user lands back on the form they just filled in, which also kills
        // every coroutine the shell had started.
        credentials.save(ACCOUNT, PASSWORD) // what `IdsSessionRepository.login` does before `Success`
        gate.onStatus(IdsLoginStatus.success())
        assertTrue(gate.loggedIn.value)

        gate.onStatus(IdsLoginStatus(phase = IdsLoginPhase.RestoringSession, stepLabel = "正在检查登录状态"))
        assertTrue(gate.loggedIn.value, "A background login must not throw the user out of the shell.")

        gate.onStatus(IdsLoginStatus(phase = IdsLoginPhase.LoggingIn, stepLabel = "正在登录"))
        assertTrue(gate.loggedIn.value)

        gate.onStatus(IdsLoginStatus(phase = IdsLoginPhase.AwaitingCaptcha, stepLabel = "正在处理验证码"))
        assertTrue(gate.loggedIn.value)
    }

    @Test
    fun aFirstLoginInFlightLeavesTheLoginScreenAlone() {
        // The same phases, on a device that has never logged in: the form stays put while it is being
        // answered rather than swapping to a shell that has nothing to show yet.
        gate.onStatus(IdsLoginStatus(phase = IdsLoginPhase.RestoringSession))
        assertFalse(gate.loggedIn.value)

        gate.onStatus(IdsLoginStatus(phase = IdsLoginPhase.LoggingIn))
        assertFalse(gate.loggedIn.value)

        gate.onStatus(IdsLoginStatus(phase = IdsLoginPhase.AwaitingReAuth))
        assertFalse(gate.loggedIn.value)
    }

    @Test
    fun aFailedRefreshKeepsTheShell() = runBlocking {
        // Not a log-out: the credentials may still be good and the cookie may just have lapsed, so the
        // caches on screen are still worth showing. Answering with the login form would both lose them
        // and be the screen change the gate exists to avoid.
        credentials.save(ACCOUNT, PASSWORD)
        gate.onStatus(IdsLoginStatus.success())

        gate.onStatus(IdsLoginStatus.failed("网络不可用"))
        assertTrue(gate.loggedIn.value)

        gate.onStatus(IdsLoginStatus.passwordWrong("用户名或密码有误"))
        assertTrue(gate.loggedIn.value)
    }

    @Test
    fun noCredentialsOutrankTheStoredFlag() = runBlocking {
        // A flag with nothing behind it — an app restored onto a device whose keystore key is gone — is
        // stale, and the repository says so with this phase. The flag is deliberately still `true` here.
        credentials.save(ACCOUNT, PASSWORD)
        val gate = LoginGate(credentials::hasCompletedLogin)
        assertTrue(gate.loggedIn.value)

        gate.onStatus(IdsLoginStatus(phase = IdsLoginPhase.NeedsCredentials, message = "请先登录"))

        assertFalse(gate.loggedIn.value)
    }

    @Test
    fun loggingOutReturnsToTheLoginScreen() = runBlocking {
        credentials.save(ACCOUNT, PASSWORD)
        gate.onStatus(IdsLoginStatus.success())
        assertTrue(gate.loggedIn.value)

        // `IdsSessionRepository.logout`: the credentials go first, then the phase.
        credentials.clear()
        gate.onStatus(IdsLoginStatus(phase = IdsLoginPhase.NeedsCredentials, message = "已退出登录"))
        assertFalse(gate.loggedIn.value)

        // And a login that was already in flight when the user logged out cannot put the shell back:
        // the phase it reports is not a success, and the store no longer backs it.
        gate.onStatus(IdsLoginStatus(phase = IdsLoginPhase.RestoringSession))
        assertFalse(gate.loggedIn.value)
    }

    @Test
    fun loggingBackInAfterALogOutReturnsToTheShell() = runBlocking {
        gate.onStatus(IdsLoginStatus(phase = IdsLoginPhase.NeedsCredentials))
        assertFalse(gate.loggedIn.value)

        credentials.save(ACCOUNT, PASSWORD)
        gate.onStatus(IdsLoginStatus.success())

        assertTrue(gate.loggedIn.value)
    }

    private fun credentialStore() = CredentialStore(
        secureStore = InMemorySecureStore(),
        settings = SettingsStore(InMemorySettings()),
        idsCrypto = IdsCrypto(AesCbc()),
    )

    private companion object {
        const val ACCOUNT = "20210001"
        const val PASSWORD = "secret"
    }
}
