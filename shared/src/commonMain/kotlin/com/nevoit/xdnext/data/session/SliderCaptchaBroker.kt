package com.nevoit.xdnext.data.session

import com.nevoit.xdnext.data.ids.CaptchaTrackPoint
import com.nevoit.xdnext.data.ids.IdsAuthApi
import com.nevoit.xdnext.data.ids.SliderCaptchaChallenge
import com.nevoit.xdnext.data.ids.SliderCaptchaSolver
import com.nevoit.xdnext.data.ids.SliderCaptchaVerifier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lets the user solve a slider captcha by hand, as the fallback the original always shipped.
 *
 * This is the manual half of the original's `solve(manualSolver:)`. It owns the refresh loop that the
 * original kept inside the solver: when a drag is rejected the challenge is spent, so the dialog can
 * fetch a fresh one and let the user try again — which is why the session only ever runs one round.
 *
 * Same handoff shape as [IdsReAuthBroker]: publish the challenge, park the caller on a deferred.
 */
class SliderCaptchaBroker(
    private val api: IdsAuthApi,
    private val verifier: SliderCaptchaVerifier,
) : SliderCaptchaSolver {

    private var pending: CompletableDeferred<Boolean>? = null

    private val _challenge = MutableStateFlow<SliderCaptchaChallenge?>(null)

    /** The challenge being solved, or null. Drives whether the captcha screen is shown. */
    val challenge: StateFlow<SliderCaptchaChallenge?> = _challenge.asStateFlow()

    override suspend fun solve(challenge: SliderCaptchaChallenge): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pending = deferred
        // The automatic solver fetches challenges of its own, and every fetch replaces the answer the
        // server is holding. The challenge handed to this solver can therefore be stale, and the user
        // would be solving a picture the server is no longer checking. Fetch a fresh one.
        _challenge.value = api.fetchSliderCaptcha() ?: challenge
        try {
            return deferred.await()
        } finally {
            _challenge.value = null
            pending = null
        }
    }

    /**
     * Submits the user's recorded drag.
     *
     * @return true when the server accepted it; false means the caller should let the user retry, which
     *   normally means calling [refresh] first because a rejected challenge is single-use.
     */
    suspend fun submit(tracks: List<CaptchaTrackPoint>): Boolean {
        val challenge = _challenge.value ?: return false
        val accepted = verifier.verify(challenge, tracks)
        if (accepted) pending?.complete(true)
        return accepted
    }

    /** Replaces the displayed challenge with a fresh one. Returns null when the fetch failed. */
    suspend fun refresh(): SliderCaptchaChallenge? {
        val fresh = api.fetchSliderCaptcha() ?: return null
        _challenge.value = fresh
        return fresh
    }

    /** The user gave up. Reports failure so the login flow continues without a solution. */
    fun cancel() {
        pending?.complete(false)
    }
}

/**
 * Tries each solver in order and stops at the first success.
 *
 * Order matters: the automatic solver goes first because it is fast and usually right, and the
 * interactive one second because it always works eventually. Both receive the *same* challenge, matching
 * the original, where the manual solver was handed the provider the automatic one had just failed with.
 */
class ChainedSliderCaptchaSolver(
    private val solvers: List<SliderCaptchaSolver>,
) : SliderCaptchaSolver {

    override suspend fun solve(challenge: SliderCaptchaChallenge): Boolean {
        for (solver in solvers) {
            if (solver.solve(challenge)) return true
        }
        return false
    }
}
