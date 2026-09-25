package com.nevoit.xdnext.data.ids

import com.nevoit.xdnext.core.image.decodeCaptchaImage
import com.nevoit.xdnext.core.log.appLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * Solves the slider captcha without the user: locate the fragment, then submit a synthetic drag.
 *
 * ## The pause is not optional
 *
 * The original awaited `max(tracks.last.c - 100, 0)` — roughly 300 ms — before every submission, and
 * that line is **load bearing**. Running the original's own code with the pause removed scores **0 out of
 * 8** against the live server; with it, **8 out of 8**. The server evidently refuses a verification that
 * arrives too soon, and every early attempt to port this failed for exactly that reason while appearing
 * to be a matcher problem.
 *
 * ## Retrying on fresh challenges
 *
 * The original also refreshes the captcha and retries, up to six times. Measured, a single round solves
 * six challenges in eight and the retries cover the rest, so the retry is worth keeping but not the main
 * event. [ChallengeProvider] is how this class asks for a new one.
 *
 * ## Choosing the position
 *
 * The match is a properly normalised correlation, and it is rescaled from background pixels into the
 * protocol's canvas units before submission. Both score functions — this one and the original's
 * un-normalised slope — were measured at 8/8 inside the original's structure, so the score is not the
 * sensitive part; the pause is.
 */
class SliderCaptchaAutoSolver(
    private val verifier: SliderCaptchaVerifier,
    private val challengeProvider: ChallengeProvider,
    private val random: Random = Random.Default,
    private val rounds: Int = DEFAULT_ROUNDS,
    private val minimumScore: Double = DEFAULT_MINIMUM_SCORE,
) : SliderCaptchaSolver {

    /** Supplies a fresh challenge, so a rejected one can be replaced instead of reused. */
    fun interface ChallengeProvider {
        suspend fun fetch(): SliderCaptchaChallenge?
    }

    override suspend fun solve(challenge: SliderCaptchaChallenge): Boolean {
        var current: SliderCaptchaChallenge? = challenge
        for (round in 1..rounds) {
            val active = current ?: return false
            if (attempt(active)) return true
            if (round < rounds) current = challengeProvider.fetch()
        }
        return false
    }

    private suspend fun attempt(challenge: SliderCaptchaChallenge): Boolean {
        val puzzle = decodeCaptchaImage(challenge.puzzleImage)
        val piece = decodeCaptchaImage(challenge.pieceImage)
        if (puzzle == null || piece == null) {
            appLog.w { "Slider captcha images could not be decoded; falling back" }
            return false
        }

        val match = SliderCaptchaMath.findMatch(puzzle, piece)
        if (match == null) {
            appLog.w { "Slider captcha fragment could not be located; falling back" }
            return false
        }
        if (match.score < minimumScore) {
            appLog.i {
                "Slider captcha best match scored ${match.score} (trim ${match.border}), below " +
                        "$minimumScore; asking the user instead"
            }
            return false
        }

        // The match is in background pixels and the background is larger than the protocol canvas, so
        // the answer must be rescaled. Sending a pixel offset against a canvasLength of 280 would put
        // the answer roughly twice as far right as it belongs.
        val canvasOffset =
            (match.offsetPixels.toDouble() / puzzle.width * challenge.canvasWidth).roundToInt()

        appLog.i {
            "Slider captcha matched at x=${match.offsetPixels} (score ${match.score}, " +
                    "trim ${match.border}) -> canvas $canvasOffset"
        }

        for (delta in SliderCaptchaMath.OFFSET_DELTAS) {
            val move = canvasOffset + delta
            if (move !in 0..challenge.canvasWidth) continue

            val tracks = SliderCaptchaMath.generateTracks(move, random)

            // The server refuses a submission that arrives too soon. This mirrors the original's
            // `max(tracks.last.c - 100, 0)`, which is what makes the whole path work.
            delay(max(tracks.last().millis - SUBMIT_EARLY_BY_MILLIS, 0).toLong().milliseconds)

            val accepted = runCatching { verifier.verify(challenge, tracks) }
                .onFailure { error ->
                    // Same reason as in `IdsSessionRepository.solveCaptcha`: a torn-down caller is not a
                    // rejected submission, and logging it as one sends whoever reads the log looking for
                    // a captcha bug that does not exist.
                    if (error is CancellationException) throw error
                    appLog.w(error) { "Slider captcha submission failed" }
                }
                .getOrDefault(false)
            if (accepted) {
                appLog.i { "Slider captcha solved automatically at canvas $move" }
                return true
            }
        }
        return false
    }

    private companion object {
        /**
         * Rounds over fresh challenges.
         *
         * The original used six. Measured, one round solves six in eight; three covers the rest without
         * letting a hopeless case hold up the login for long.
         */
        const val DEFAULT_ROUNDS = 3

        /**
         * How much sooner than the gesture's own duration the submission is sent.
         *
         * The original's constant. The tracks report 300-500 ms per step, so this is a 200-400 ms pause.
         */
        const val SUBMIT_EARLY_BY_MILLIS = 100

        /**
         * Below this the match is treated as no match and the user is asked to drag.
         *
         * A genuine match scores above 0.9 and the best score when nothing matches sits near 0.4, so the
         * threshold errs towards asking the user rather than submitting a guess.
         */
        const val DEFAULT_MINIMUM_SCORE = 0.75
    }
}
