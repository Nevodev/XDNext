package com.nevoit.xdnext.data.session

import com.nevoit.xdnext.core.log.appLog
import com.nevoit.xdnext.data.ids.CaptchaSolveFailedException
import com.nevoit.xdnext.data.ids.IdsAuthApi
import com.nevoit.xdnext.data.ids.IdsAuthException
import com.nevoit.xdnext.data.ids.IdsCrypto
import com.nevoit.xdnext.data.ids.IdsEndpoints
import com.nevoit.xdnext.data.ids.IdsLoginPageParser
import com.nevoit.xdnext.data.ids.IdsLoginPhase
import com.nevoit.xdnext.data.ids.IdsLoginStatus
import com.nevoit.xdnext.data.ids.IdsProtocolException
import com.nevoit.xdnext.data.ids.IdsReAuthClient
import com.nevoit.xdnext.data.ids.IdsReAuthHandler
import com.nevoit.xdnext.data.ids.IdsReAuthRequiredException
import com.nevoit.xdnext.data.ids.LoginFailedException
import com.nevoit.xdnext.data.ids.MissingCredentialsException
import com.nevoit.xdnext.data.ids.NoCaptchaSolver
import com.nevoit.xdnext.data.ids.PasswordWrongException
import com.nevoit.xdnext.data.ids.SliderCaptchaSolver
import com.nevoit.xdnext.data.ids.isIdsReAuthLocation
import com.nevoit.xdnext.data.net.resolveUrl
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The IDS login state machine — the heart of "automatic login".
 *
 * Ported from `IDSSession.checkAndLogin` / `_loginOnce` / `_completeRedirect`,
 * `HomepageController._comboLogin` and the re-auth branch of `resolveIDSReAuthIfNeeded`, with three
 * structural changes:
 *
 *  - the flow returns a `Result` and publishes a [status] flow, instead of mutating a **global**
 *    `loginState` variable that every module read and wrote. The original's global meant a failed
 *    background refresh could silently flip the UI's login state.
 *  - logins are serialised by a [Mutex]. The original used a `synchronized` `Lock` for a real reason:
 *    CAS service tickets are single-use and target-bound, so two concurrent logins would steal each
 *    other's ticket.
 *  - the two interactive steps (slider captcha and second factor) arrive as injected dependencies
 *    rather than as a global handler plus a `BuildContext`. A test can supply neither and still drive
 *    the whole non-interactive flow.
 *
 * The automatic path is [restoreSession]: it probes the service first and only falls back to a password
 * login when the persisted cookie is no longer good. That probe is what makes a warm start cheap.
 */
class IdsSessionRepository(
    private val api: IdsAuthApi,
    private val idsCrypto: IdsCrypto,
    private val credentials: CredentialStore,
    private val reAuthHandler: IdsReAuthHandler? = null,
) {

    private val loginMutex = Mutex()

    private val _status = MutableStateFlow(IdsLoginStatus.idle())
    val status: StateFlow<IdsLoginStatus> = _status.asStateFlow()

    /**
     * Logs in without asking the user anything, using the stored session and then the stored
     * credentials.
     *
     * Fails with [MissingCredentialsException] when nothing was ever saved — the caller should show
     * the login screen rather than treat that as an error worth reporting.
     */
    suspend fun restoreSession(
        service: String = IdsEndpoints.EHALL_SERVICE,
        captcha: SliderCaptchaSolver = NoCaptchaSolver,
    ): Result<Unit> = loginMutex.withLock {
        update(IdsLoginPhase.RestoringSession, 10, "正在检查登录状态")

        val account = credentials.account()
        val password = credentials.password()
        if (account == null || password == null) {
            dropStaleLoginFlag(credentials)
            _status.value = IdsLoginStatus(
                phase = IdsLoginPhase.NeedsCredentials,
                message = "请先登录",
            )
            return@withLock Result.failure(MissingCredentialsException())
        }

        return@withLock runCatching {
            completeRedirect(authenticate(account, password, service, captcha), account, service)
        }.fold(
            onSuccess = {
                _status.value = IdsLoginStatus.success()
                Result.success(Unit)
            },
            onFailure = { error ->
                _status.value = error.toStatus()
                Result.failure(error)
            },
        )
    }

    /**
     * Logs in with credentials the user just typed.
     *
     * Credentials are persisted only after the server accepts them, matching the original, so a typo
     * never becomes the stored credential.
     */
    suspend fun login(
        account: String,
        password: String,
        service: String = IdsEndpoints.EHALL_SERVICE,
        captcha: SliderCaptchaSolver = NoCaptchaSolver,
    ): Result<Unit> = loginMutex.withLock {
        return@withLock runCatching {
            // The original wiped the jar before every manual login so a half-established session could
            // not confuse the new one.
            api.clearCookies()
            completeRedirect(authenticate(account, password, service, captcha), account, service)
            credentials.save(account, password)
        }.fold(
            onSuccess = {
                _status.value = IdsLoginStatus.success()
                Result.success(Unit)
            },
            onFailure = { error ->
                _status.value = error.toStatus()
                Result.failure(error)
            },
        )
    }

    /** Forgets both the session and the stored credentials. */
    suspend fun logout() = loginMutex.withLock {
        api.clearCookies()
        credentials.clear()
        _status.value = IdsLoginStatus(IdsLoginPhase.NeedsCredentials, message = "已退出登录")
    }

    // ---------------------------------------------------------------------------------------------
    // Service logins for the business modules
    // ---------------------------------------------------------------------------------------------

    /**
     * Logs in for [service] and returns the **first** `Location` of the resulting redirect chain.
     *
     * This is the original's `IDSSession.checkAndLogin`, and it is deliberately not the same call as
     * [restoreSession]: a module such as the energy system needs the chain's *end*, because the target
     * application appends a one-time `code` to it. Walking the chain here and throwing the answer away
     * would leave that module with no way to get one.
     *
     * The login lock is held for this call and released before [followRedirects], matching the
     * original: the lock exists so two logins cannot steal each other's single-use service ticket, and
     * following the chain is not part of that.
     */
    suspend fun loginForService(
        service: String,
        captcha: SliderCaptchaSolver = NoCaptchaSolver,
    ): Result<String> = loginMutex.withLock {
        val account = credentials.account()
        val password = credentials.password()
        if (account == null || password == null) {
            dropStaleLoginFlag(credentials)
            _status.value = IdsLoginStatus(
                phase = IdsLoginPhase.NeedsCredentials,
                message = "请先登录",
            )
            return@withLock Result.failure(MissingCredentialsException())
        }

        runCatching { authenticate(account, password, service, captcha) }.fold(
            onSuccess = { location ->
                _status.value = IdsLoginStatus.success()
                Result.success(location)
            },
            onFailure = { error ->
                _status.value = error.toStatus()
                Result.failure(error)
            },
        )
    }

    /**
     * Walks a CAS redirect chain to its end and returns the last response.
     *
     * Ported from `IDSSession.followIDSRedirects`. Following the chain is not cosmetic: each hop is
     * what makes the destination service set its own session cookie in the shared jar, and the final
     * URL is where a module like the energy system finds its `code`.
     *
     * @param account the username a second-factor challenge would be answered for; defaults to the
     *   stored account, which is what the original did.
     * @param service the CAS service the chain started from. Overridden by the `service` parameter of
     *   any `/authserver/login` hop, again as in the original.
     */
    suspend fun followRedirects(
        initialLocation: String,
        account: String? = null,
        service: String? = null,
    ): HttpResponse {
        if (initialLocation.isEmpty()) {
            throw IdsProtocolException("统一认证跳转响应缺少 Location")
        }

        val username = account ?: credentials.account().orEmpty()
        var currentService = idsLoginServiceOf(initialLocation) ?: service
        var current = resolveSecondFactor(
            resolveUrl(IdsEndpoints.IDS_ORIGIN + "/", initialLocation),
            username,
            currentService,
        )
        var hops = 0

        update(IdsLoginPhase.LoggingIn, 80, "正在完成登录跳转")

        while (hops < IdsEndpoints.MAX_AUTH_REDIRECTS) {
            val response = api.fetch(current)
            val location = response.location() ?: return response

            // Drain the body so the connection is released; the content itself is not needed.
            response.bodyAsText()

            hops++
            val next = resolveUrl(current, location)
            currentService = idsLoginServiceOf(next) ?: currentService
            current = resolveSecondFactor(next, username, currentService)
        }

        throw LoginFailedException("统一认证跳转次数超过 ${IdsEndpoints.MAX_AUTH_REDIRECTS} 次")
    }

    /** The account the stored credentials belong to, for modules that have to name it. */
    suspend fun currentAccount(): String? = credentials.account()

    /**
     * The `service` parameter of an IDS login hop, or null when [location] is not one.
     *
     * A later redirect can send the chain back through the login page with a different service, and
     * the original re-read it at every hop rather than assuming the one it started with.
     */
    private fun idsLoginServiceOf(location: String): String? {
        val url = runCatching { Url(location) }.getOrNull() ?: return null
        if (url.host != "ids.xidian.edu.cn" || url.encodedPath != "/authserver/login") return null
        return url.parameters["service"]?.takeIf { it.isNotEmpty() }
    }

    /** Collects the first `Location` of a chain and walks it, for callers that want the session only. */
    private suspend fun completeRedirect(
        initialLocation: String,
        account: String,
        service: String
    ) {
        followRedirects(initialLocation = initialLocation, account = account, service = service)
    }

    // ---------------------------------------------------------------------------------------------
    // Flow
    // ---------------------------------------------------------------------------------------------

    /** Runs a login and returns the first `Location` of the chain it produces. */
    private suspend fun authenticate(
        account: String,
        password: String,
        service: String,
        captcha: SliderCaptchaSolver,
    ): String {
        update(IdsLoginPhase.RestoringSession, 20, "正在检查登录状态")
        val probe = api.fetchLoginPage(service)

        if (probe.isRedirect()) {
            // Warm path: the persisted cookie is still valid. This is what "automatic login" means.
            update(IdsLoginPhase.RestoringSession, 60, "已复用登录状态")
            return probe.location().orEmpty()
        }

        if (probe.status == HttpStatusCode.Unauthorized) {
            throw failureFor(probe.bodyAsText())
        }

        val html = probe.bodyAsText()
        appLog.i {
            "[IdsSessionRepository] Login probe answered HTTP ${probe.status.value}, " +
                    "${html.length} chars, title=${IdsLoginPageParser.pageTitle(html) ?: "<none>"}, " +
                    "loginPage=${IdsLoginPageParser.looksLikeLoginPage(html)}, " +
                    "notRegistered=${IdsLoginPageParser.isApplicationNotRegistered(html)}"
        }

        if (IdsLoginPageParser.isApplicationNotRegistered(html)) {
            // A 200 that is neither a redirect nor a form. IDS is refusing the `service` itself, so no
            // amount of retrying or re-parsing helps: the target application has to be the one IDS has
            // registered. Named separately because the generic parse error below would blame markup.
            throw IdsProtocolException(
                "统一认证拒绝了该 service：目标应用未在认证平台注册（${
                    IdsLoginPageParser.pageTitle(
                        html
                    ) ?: "应用未注册"
                }）",
            )
        }

        val form = IdsLoginPageParser.parse(html)
            ?: throw IdsProtocolException(
                "无法解析统一认证登录页（缺少字段：" +
                        IdsLoginPageParser.missingFields(html).joinToString("、") + "）",
            )

        update(IdsLoginPhase.LoggingIn, 30, "正在注册设备指纹")
        api.registerBrowserFingerprint(credentials.browserFingerprint())

        val fields = linkedMapOf(
            "username" to account,
            // The salt comes from the page just fetched and is the AES key; a stale page means a stale
            // salt, which is why the form is never cached.
            "password" to idsCrypto.encryptPassword(password, form.passwordEncryptSalt),
            "rememberMe" to "true",
            "cllt" to "userNameLogin",
            "dllt" to "generalLogin",
            "_eventId" to "submit",
            "lt" to form.lt,
            "execution" to form.execution,
        )

        update(IdsLoginPhase.LoggingIn, 45, "正在准备验证码")
        api.primeSliderCaptcha()
        val captchaSolved = solveCaptcha(captcha)
        if (!captchaSolved) {
            // Not fatal: a device the server already trusts does not always demand a captcha, and the
            // original's unconditional solve was part of why it could not log in without one.
            appLog.i { "No slider captcha solution available; attempting login anyway" }
        }

        update(IdsLoginPhase.LoggingIn, 60, "正在登录")
        val response = api.postLogin(fields, service)
        return handleLoginResponse(response, captchaSolved)
    }

    private suspend fun handleLoginResponse(
        response: HttpResponse,
        captchaSolved: Boolean
    ): String {
        if (response.isRedirect()) {
            return response.location().orEmpty()
        }

        if (response.status == HttpStatusCode.Unauthorized) {
            throw failureFor(response.bodyAsText())
        }

        val body = response.bodyAsText()

        // The server sometimes answers a successful credential check with a second form to submit
        // rather than a redirect.
        val continueFields = IdsLoginPageParser.parseContinueForm(body)
        if (continueFields != null) {
            val second = api.postContinueForm(continueFields)
            if (second.isRedirect()) {
                return second.location().orEmpty()
            }
            throw LoginFailedException("登录失败：二次提交未被接受 (HTTP ${second.status.value})")
        }

        val tip = IdsLoginPageParser.parseErrorTip(body)
        if (tip != null) throw LoginFailedException(tip)
        if (!captchaSolved) throw CaptchaSolveFailedException()

        throw LoginFailedException("登录失败：服务器未返回跳转 (HTTP ${response.status.value})")
    }

    /**
     * Answers a second-factor challenge if [location] is one, otherwise returns it unchanged.
     *
     * Without a handler this reports [IdsReAuthRequiredException] rather than skipping the challenge —
     * skipping would look like a successful login that mysteriously has no session.
     */
    private suspend fun resolveSecondFactor(
        location: String,
        account: String,
        service: String?,
    ): String {
        if (!isIdsReAuthLocation(location)) return location

        val handler = reAuthHandler ?: throw IdsReAuthRequiredException()
        update(IdsLoginPhase.AwaitingReAuth, 70, "需要二次认证")

        val client = IdsReAuthClient(
            api = api,
            challengeUri = location,
            username = account,
            service = service,
            registerBrowserFingerprint = {
                api.registerBrowserFingerprint(credentials.browserFingerprint())
            },
        )
        return handler.handle(client)
    }

    /**
     * Solves the captcha once, on a single challenge.
     *
     * The original wrapped its solver in a six-round refetch loop and the solver itself wrapped a
     * seven-candidate loop. Only one of those is needed per layer: the automatic solver already spends
     * several submissions on a challenge, and the interactive solver owns its own refresh so the user
     * is not shown the same dialog twice.
     */
    private suspend fun solveCaptcha(solver: SliderCaptchaSolver): Boolean {
        if (solver === NoCaptchaSolver) return false

        update(IdsLoginPhase.AwaitingCaptcha, 50, "正在处理验证码")
        val challenge = api.fetchSliderCaptcha() ?: return false

        return runCatching { solver.solve(challenge) }
            .onFailure { error ->
                // A cancelled login is not a solver failure. `runCatching` catches `CancellationException`
                // like any other throwable, so without this the cancellation is logged as "solver failed"
                // — which is how a caller tearing the work down (a composition leaving, a screen being
                // closed) reported itself as a captcha error — and the login then carried on to post a
                // password from a coroutine that had already been cancelled.
                if (error is CancellationException) throw error
                appLog.w(error) { "Slider captcha solver failed" }
            }
            .getOrDefault(false)
    }

    /**
     * Classifies a 401 from the login endpoint.
     *
     * Confirmed against the live server: a missing or wrong slider captcha is rejected with **401** and
     * the tip `图形动态验证码错误`, so a 401 does not imply a bad password. Getting this backwards tells a
     * user whose credentials are fine that their password is wrong, and sends them to the wrong fix.
     */
    private fun failureFor(body: String): IdsAuthException {
        val tip = IdsLoginPageParser.parseErrorTip(body)
            ?: return PasswordWrongException("用户名或密码有误")
        return if (IdsLoginPageParser.isCaptchaError(tip)) {
            CaptchaSolveFailedException(tip)
        } else {
            PasswordWrongException(tip)
        }
    }

    private fun update(phase: IdsLoginPhase, progress: Int, label: String) {
        _status.value = IdsLoginStatus(phase = phase, progress = progress, stepLabel = label)
    }

    /**
     * Removes a "a login has completed here" flag that no longer has credentials behind it.
     *
     * That flag is written with the credentials and read *before the first frame* to choose between the
     * login screen and the shell, so it has to mean exactly what it says. It stops meaning it when the
     * secure store cannot produce the credentials — most often an invalidated keystore key, which
     * [com.nevoit.xdnext.core.store.SecureStore] treats as absent — and a launch that trusts it would
     * compose the shell, discover there is nothing to log in with, and land on the login screen: the
     * flash the flag exists to prevent, repeated on every launch. Dropping it here means the next launch
     * opens where this one ended up.
     */
    private suspend fun dropStaleLoginFlag(credentials: CredentialStore) {
        if (credentials.hasCompletedLogin()) {
            appLog.i { "[IdsSessionRepository] No stored credentials; clearing the completed-login flag" }
            credentials.clear()
        }
    }

    private fun Throwable.toStatus(): IdsLoginStatus = when (this) {
        is PasswordWrongException -> IdsLoginStatus.passwordWrong(message ?: "用户名或密码有误")
        is IdsAuthException -> IdsLoginStatus.failed(message ?: "登录失败")
        else -> IdsLoginStatus.failed(message ?: this::class.simpleName ?: "登录失败")
    }
}

private fun HttpResponse.location(): String? = headers[HttpHeaders.Location]

private fun HttpResponse.isRedirect(): Boolean = status.value in 300..399
