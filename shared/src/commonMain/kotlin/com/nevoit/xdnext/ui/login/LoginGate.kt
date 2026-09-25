package com.nevoit.xdnext.ui.login

import com.nevoit.xdnext.data.ids.IdsLoginPhase
import com.nevoit.xdnext.data.ids.IdsLoginStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The gate between the login screen and the shell: which of the two the composition should show.
 *
 * Two inputs decide it — whether a login has ever completed on this device, and where the login flow
 * currently is. The first is asked of a **store** on every judgement rather than captured once, and that
 * is the entire reason this is a class instead of two lines in the view model.
 *
 * The value changes underneath us: a successful login writes it. A copy taken at start-up is wrong the
 * moment the user logs in, and wrong in the worst possible way — the shell appears, the shell's own card
 * refresh logs in for *its* service, that reports a phase which is not [IdsLoginPhase.Success], the stale
 * copy answers "no login has ever completed here", and the user is thrown back to the login form they
 * just filled in. That is not hypothetical: it is the flash-to-shell-and-back this class exists to
 * prevent, and a test pins it.
 *
 * Kept out of [LoginViewModel] so the rule can be driven directly, with no composition and no
 * `Dispatchers.Main`.
 */
internal class LoginGate(
    /**
     * Whether a completed login is on disk *right now*.
     *
     * Called once per status change. It is a synchronous preference read — see
     * [com.nevoit.xdnext.data.session.CredentialStore.hasCompletedLogin] — so it costs a map lookup and
     * can be asked from the first composition as well as from a collector.
     */
    private val hasCompletedLogin: () -> Boolean,
) {

    private val _loggedIn = MutableStateFlow(decide(IdsLoginPhase.Idle))

    /**
     * Whether the shell is what should be on screen.
     *
     * Correct from construction, which is what lets the composition read it before its first frame and
     * open on the right screen instead of navigating to it.
     */
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    /**
     * Judges a phase. Every login in the app reports through here, the user's own and a background
     * refresh's alike.
     */
    fun onStatus(status: IdsLoginStatus) {
        _loggedIn.value = decide(status.phase)
    }

    /**
     * Three cases, and the third is the one worth stating:
     *
     *  - [IdsLoginPhase.Success] — a login has just completed, so the shell. This is what carries the
     *    user from the login form to the home screen, and it is deliberately not a navigation call:
     *    nothing is pushed, so nothing has to be popped, and back cannot return to a form already
     *    answered.
     *  - [IdsLoginPhase.NeedsCredentials] — there is nothing left to log in with, so the login screen.
     *    It outranks the store: this is the phase a log-out leaves behind and the one an empty secure
     *    store produces, and a flag that disagrees with it is stale.
     *  - everything else — whatever is on disk. [IdsLoginPhase.Failed] and
     *    [IdsLoginPhase.PasswordWrong] are *attempts* that failed, and a device that has logged in before
     *    keeps the shell: the credentials may still be good, a background refresh is allowed to fail, and
     *    answering a failed refresh with the login form would both lose the caches on screen and be a
     *    screen change nobody asked for. [IdsLoginPhase.RestoringSession], [IdsLoginPhase.LoggingIn] and
     *    the two challenge phases are the same answer for a different reason — they are what the shell's
     *    own refreshes report while they work, and reading them as "not logged in" is exactly the bug
     *    this rule is written around.
     */
    private fun decide(phase: IdsLoginPhase): Boolean = when (phase) {
        IdsLoginPhase.Success -> true
        IdsLoginPhase.NeedsCredentials -> false
        else -> hasCompletedLogin()
    }
}
