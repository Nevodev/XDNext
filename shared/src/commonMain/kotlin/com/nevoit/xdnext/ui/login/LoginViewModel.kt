package com.nevoit.xdnext.ui.login

import com.nevoit.xdnext.data.ids.CaptchaTrackPoint
import com.nevoit.xdnext.data.ids.IdsCodeDelivery
import com.nevoit.xdnext.data.ids.IdsLoginStatus
import com.nevoit.xdnext.data.ids.IdsReAuthCancelledException
import com.nevoit.xdnext.data.ids.IdsReAuthClient
import com.nevoit.xdnext.data.ids.IdsReAuthCodeType
import com.nevoit.xdnext.data.ids.IdsReAuthExpiredException
import com.nevoit.xdnext.data.ids.SliderCaptchaChallenge
import com.nevoit.xdnext.data.ids.SliderCaptchaSolver
import com.nevoit.xdnext.data.session.CredentialStore
import com.nevoit.xdnext.data.session.IdsReAuthBroker
import com.nevoit.xdnext.data.session.IdsSessionRepository
import com.nevoit.xdnext.data.session.SliderCaptchaBroker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the login screen with a one-way data flow.
 *
 * Intentionally **app-scoped rather than screen-scoped**. The login session outlives any screen — it is
 * what every later module depends on — so tying it to a composable's lifetime (or an Android
 * `ViewModelStore`) would mean re-authenticating on every navigation. The original made the same choice,
 * but expressed it as a mutable global; this keeps the state observable instead.
 *
 * Two interactive steps can interrupt a login, and both are surfaced here as state: [captchaChallenge]
 * and [reAuthChallenge]. The dialogs are driven by those flows rather than by a navigation call from
 * deep inside the network layer, so a background refresh can raise a challenge just as well as a
 * foreground login can.
 *
 * It also owns the one decision the composition root needs before it draws anything — [loggedIn], through
 * a [LoginGate] seeded from what is on disk rather than from a request.
 */
class LoginViewModel(
    private val session: IdsSessionRepository,
    private val credentials: CredentialStore,
    private val reAuthBroker: IdsReAuthBroker,
    private val captchaBroker: SliderCaptchaBroker,
    private val captchaSolver: SliderCaptchaSolver,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Decides which of the two screens the composition shows.
     *
     * Its answer is correct from here, in the constructor, which is the last moment before the first
     * composition asks for it: the flag behind it is a synchronous preference read, not a request, so a
     * warm start composes the shell directly rather than composing the login screen and navigating away
     * from it. See [LoginGate].
     */
    private val gate = LoginGate(credentials::hasCompletedLogin)

    /**
     * Whether the shell is what should be on screen, rather than [LoginScreen].
     *
     * Seeded from disk, then kept up to date from [IdsSessionRepository.status], which every login in
     * the app reports to — the user's own, and a background refresh's. The composition swaps between
     * the two screens on this value; neither is a page of the navigator, so nothing is pushed,
     * popped or transitioned between.
     */
    val loggedIn: StateFlow<Boolean> = gate.loggedIn

    /** Login progress, owned by the repository so background refreshes report here too. */
    val status: StateFlow<IdsLoginStatus> = session.status

    init {
        scope.launch {
            session.status.collect { status -> gate.onStatus(status) }
        }
    }

    /** Non-null while the server is waiting for a second-factor code. */
    val reAuthChallenge: StateFlow<IdsReAuthClient?> = reAuthBroker.challenge

    /** Non-null while a slider captcha needs a human. */
    val captchaChallenge: StateFlow<SliderCaptchaChallenge?> = captchaBroker.challenge

    private val _account = MutableStateFlow("")
    val account: StateFlow<String> = _account.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _passwordVisible = MutableStateFlow(false)
    val passwordVisible: StateFlow<Boolean> = _passwordVisible.asStateFlow()

    /**
     * Prefills the account field, then attempts a silent login when credentials are stored.
     *
     * Called once from the composition, which is the only place that knows the app has started.
     * Prefilling matters because the original did the same and users expect to only retype the
     * password; the silent login is what makes a warm start warm — the persisted cookie is probed
     * first and a password login is only attempted when it is no longer good. See
     * [IdsSessionRepository.restoreSession].
     *
     * A device with nothing stored does neither, so this is also the correct call on a first run:
     * the account comes back empty and no request is made.
     */
    fun start() {
        scope.launch {
            _account.value = credentials.account().orEmpty()
            if (credentials.hasStoredCredentials()) {
                session.restoreSession(captcha = captchaSolver)
            }
        }
    }

    fun onAccountChange(value: String) {
        _account.value = value
    }

    fun onPasswordChange(value: String) {
        _password.value = value
    }

    fun togglePasswordVisibility() {
        _passwordVisible.value = !_passwordVisible.value
    }

    /** Signs in with what is currently typed. Empty fields are ignored rather than sent. */
    fun submit() {
        val account = _account.value.trim()
        val password = _password.value
        if (account.isEmpty() || password.isEmpty()) return
        scope.launch {
            session.login(account = account, password = password, captcha = captchaSolver)
        }
    }

    /** Clears both the session and the stored credentials, then blanks the password field. */
    fun logout() {
        scope.launch {
            session.logout()
            _password.value = ""
            _passwordVisible.value = false
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Second factor
    // ---------------------------------------------------------------------------------------------

    /** Asks the server to deliver a code. Failure is returned so the dialog can explain it. */
    suspend fun sendReAuthCode(codeType: IdsReAuthCodeType): Result<IdsCodeDelivery> {
        val client = reAuthBroker.challenge.value
            ?: return Result.failure(IdsReAuthCancelledException())

        return runCatching { client.sendCode(codeType) }
            .onFailure { error ->
                // An expired challenge cannot be recovered inside the dialog; abort the login instead
                // of leaving the user retyping codes into a dead session.
                if (error is IdsReAuthExpiredException) reAuthBroker.fail(error)
            }
    }

    /** Submits a code. A rejected code is returned; an expired challenge aborts the login. */
    suspend fun submitReAuthCode(
        codeType: IdsReAuthCodeType,
        code: String,
        trustDevice: Boolean,
    ): Result<Unit> {
        val client = reAuthBroker.challenge.value
            ?: return Result.failure(IdsReAuthCancelledException())

        return runCatching { client.submitCode(codeType, code, trustDevice) }
            .onSuccess { location -> reAuthBroker.resolve(location) }
            .onFailure { error ->
                if (error is IdsReAuthExpiredException) reAuthBroker.fail(error)
            }
            .map { }
    }

    fun cancelReAuth() {
        reAuthBroker.cancel()
    }

    // ---------------------------------------------------------------------------------------------
    // Slider captcha
    // ---------------------------------------------------------------------------------------------

    /** Submits a recorded drag. False means the caller should refresh and let the user retry. */
    suspend fun submitCaptcha(tracks: List<CaptchaTrackPoint>): Boolean =
        runCatching { captchaBroker.submit(tracks) }.getOrDefault(false)

    /** Replaces the challenge with a fresh one, because a rejected challenge is single-use. */
    suspend fun refreshCaptcha(): Boolean =
        runCatching { captchaBroker.refresh() }.getOrNull() != null

    /** The user gave up on the captcha; the login continues and will most likely fail server-side. */
    fun cancelCaptcha() {
        captchaBroker.cancel()
    }
}

