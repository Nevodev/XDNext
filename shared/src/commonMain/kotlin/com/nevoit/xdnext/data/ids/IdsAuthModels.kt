package com.nevoit.xdnext.data.ids

import com.nevoit.xdnext.data.ids.SliderCaptchaChallenge.Companion.CANVAS_WIDTH


/** Where the automatic-login state machine currently is. Drives the login screen. */
enum class IdsLoginPhase {
    /** Nothing attempted yet. */
    Idle,

    /** Reusing persisted cookies; no credentials needed unless this fails. */
    RestoringSession,

    /** No stored credentials, so the user must sign in. */
    NeedsCredentials,

    /** A password login is in flight. */
    LoggingIn,

    /** The server demanded a slider captcha and a solver is running. */
    AwaitingCaptcha,

    /** The server demanded a second factor; a dialog is collecting the code. */
    AwaitingReAuth,

    Success,

    /** The server rejected the stored credentials; the user must re-enter them. */
    PasswordWrong,

    Failed,
}

/**
 * A login attempt's observable state.
 *
 * [progress] is a 0..100 hint for a determinate progress indicator, mirroring the percentage the
 * original fed into its progress dialog, and [stepLabel] is a human-readable step name.
 */
data class IdsLoginStatus(
    val phase: IdsLoginPhase = IdsLoginPhase.Idle,
    val progress: Int = 0,
    val stepLabel: String = "",
    val message: String? = null,
) {
    val isBusy: Boolean
        get() = phase == IdsLoginPhase.RestoringSession ||
                phase == IdsLoginPhase.LoggingIn ||
                phase == IdsLoginPhase.AwaitingCaptcha ||
                phase == IdsLoginPhase.AwaitingReAuth

    companion object {
        fun idle() = IdsLoginStatus(phase = IdsLoginPhase.Idle)
        fun success() = IdsLoginStatus(phase = IdsLoginPhase.Success, progress = 100)
        fun failed(message: String) =
            IdsLoginStatus(phase = IdsLoginPhase.Failed, message = message)

        fun passwordWrong(message: String) =
            IdsLoginStatus(phase = IdsLoginPhase.PasswordWrong, message = message)
    }
}

/** Base type for everything that can go wrong during authentication. */
sealed class IdsAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The account or password was rejected.
 *
 * The original detected this from an HTTP 401 and scraped `#showErrorTip` for a friendlier message;
 * the scraped text is carried in [message] when available.
 */
class PasswordWrongException(message: String) : IdsAuthException(message)

/** The login flow completed without producing a usable session. */
class LoginFailedException(message: String) : IdsAuthException(message)

/** The server answered in a way the protocol does not define. */
class IdsProtocolException(message: String) : IdsAuthException(message)

/** The slider captcha could not be solved, automatically or manually. */
class CaptchaSolveFailedException(message: String = "验证码校验失败") : IdsAuthException(message)

/** No credentials are stored, so an automatic login cannot even be attempted. */
class MissingCredentialsException : IdsAuthException("尚未保存账号密码")

/**
 * The server asked for a second factor and no [IdsReAuthHandler] was supplied.
 *
 * With a handler installed this is never thrown; it exists so that a caller which cannot show a dialog
 * (a background refresh, a test) fails loudly instead of hanging on a challenge nobody will answer.
 */
class IdsReAuthRequiredException :
    IdsAuthException("登录需要二次认证，但当前没有可用的验证入口")

/** The user dismissed the second-factor prompt. */
class IdsReAuthCancelledException : IdsAuthException("已取消二次认证")

/** The challenge expired, or the server rejected the resumed session. */
class IdsReAuthExpiredException(message: String) : IdsAuthException(message)

/** The code was wrong, or empty. */
class IdsReAuthCodeRejectedException(message: String) : IdsAuthException(message)

/**
 * A slider captcha challenge, as returned by `GET /authserver/openSliderCaptcha.htl`.
 *
 * [pieceKey] is the last 16 bytes of [pieceImage]; the server uses it as the AES key for the signed
 * payload, so it must survive intact from download to submission.
 *
 * **The images are not the canvas.** Measured against the live server, the background is a JPEG of
 * roughly 590x360 (and the size varies between requests) and the piece is a 93x360 PNG strip
 * containing a 90x90 opaque square whose vertical position changes per challenge. The *protocol*
 * canvas, however, is a normalised [CANVAS_WIDTH] units wide, and that is the number the answer must
 * be expressed in.
 *
 * The original conflated the two: it hard-coded a 280x155 display and sent `canvasLength: 280`
 * regardless of the real image size. That happens to work because the server only uses the ratio, but
 * it means an offset computed in decoded-pixel space must be rescaled before it is sent — which is
 * exactly the mistake this comment exists to prevent.
 */
data class SliderCaptchaChallenge(
    val puzzleImage: ByteArray,
    val pieceImage: ByteArray,
    val pieceKey: ByteArray,
) {
    /** The coordinate space the answer must be expressed in. */
    val canvasWidth: Int get() = CANVAS_WIDTH

    // ByteArray gives identity equals/hashCode; fine for a short-lived challenge, but the generated
    // toString would print megabytes, so it is overridden.
    override fun toString(): String =
        "SliderCaptchaChallenge(puzzle=${puzzleImage.size}B, piece=${pieceImage.size}B, " +
                "key=${pieceKey.size}B)"

    companion object {
        /**
         * Width of the normalised canvas the server expects.
         *
         * The original's constant, kept verbatim: the server compares the answer as a proportion of
         * this value, so changing it without server knowledge would break every solution.
         */
        const val CANVAS_WIDTH = 280
    }
}

/** One point of a recorded drag track; the wire format uses terse single-letter keys. */
data class CaptchaTrackPoint(val x: Int, val y: Int, val millis: Int)

/**
 * Solves one slider captcha challenge.
 *
 * The original had two strategies behind one call site: an automatic NCC template match, and a manual
 * UI fallback. Both are useful, so the session only depends on this interface — which means the
 * automatic solver can be added later without touching the login flow, and tests can supply a stub.
 *
 * The challenge is fetched by the session, not by the solver, so that retry and refresh policy
 * (the original tried up to six refreshes) lives in one place.
 *
 * @return true when the server accepted the submitted solution.
 */
fun interface SliderCaptchaSolver {
    suspend fun solve(challenge: SliderCaptchaChallenge): Boolean
}

/** A solver that always declines, for tests and for callers that cannot show UI. */
val NoCaptchaSolver = SliderCaptchaSolver { false }
