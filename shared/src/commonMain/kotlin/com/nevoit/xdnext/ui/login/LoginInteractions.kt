package com.nevoit.xdnext.ui.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/**
 * The two interactive login steps, rendered wherever a login can happen.
 *
 * This is the other half of the handoff the brokers make. A module that logs in — the electricity
 * query, the campus card, the registrar — can be asked for a drag or a second-factor code at any
 * moment, from a background coroutine with no screen involved, and until this composable is mounted
 * somewhere that outlives every screen, **nothing answers it**: the broker parks on a deferred, the
 * session's login lock is held for the whole time it waits, and every later login queues behind it.
 * The symptom is not an error but a spinner that never stops, on whichever screen happens to be
 * showing the module that was waiting.
 *
 * So it belongs at the **root of the composition**, not inside the login screen. The login screen is
 * not on screen once a session exists, and it is precisely the background refreshes that run
 * afterwards which raise the challenges. `LoginViewModel` publishes both as state for this reason, and
 * its own documentation says so — this is the consumer that was missing.
 *
 * Both dialogs refuse an outside tap: an ambivalent dismissal would leave the login coroutine parked
 * exactly as it was. Each offers its own way out instead — [SliderCaptchaDialog] a cancel, and
 * [IdsReAuthDialog] a switch to the other delivery channel.
 */
@Composable
fun LoginInteractions(viewModel: LoginViewModel) {
    val captcha by viewModel.captchaChallenge.collectAsState()
    val reAuth by viewModel.reAuthChallenge.collectAsState()

    // Never both at once: a challenge is raised, answered or cancelled, and only then does the login
    // continue far enough to raise the next one. Rendering them independently is still the honest
    // thing, because that is a property of the protocol and not of this composition.
    captcha?.let { challenge ->
        SliderCaptchaDialog(viewModel = viewModel, challenge = challenge)
    }

    reAuth?.let { client ->
        IdsReAuthDialog(viewModel = viewModel, client = client)
    }
}
