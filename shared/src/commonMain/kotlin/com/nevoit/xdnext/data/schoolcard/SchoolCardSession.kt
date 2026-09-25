package com.nevoit.xdnext.data.schoolcard

import com.nevoit.xdnext.core.log.appLog
import com.nevoit.xdnext.data.fetch.FetchResult
import com.nevoit.xdnext.data.ids.CaptchaSolveFailedException
import com.nevoit.xdnext.data.ids.IdsAuthException
import com.nevoit.xdnext.data.ids.MissingCredentialsException
import com.nevoit.xdnext.data.ids.NoCaptchaSolver
import com.nevoit.xdnext.data.ids.PasswordWrongException
import com.nevoit.xdnext.data.ids.SliderCaptchaSolver
import com.nevoit.xdnext.data.schoolcard.SchoolCardSession.Companion.OPEN_ID_VALIDITY
import com.nevoit.xdnext.data.session.IdsSessionRepository
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The campus card as [SchoolCardRepository] sees it.
 *
 * A seam, for the same reason `EnergyDataSource` is one: the card's failure policy — a failed refresh
 * keeps the cached balance, a failed transaction query does not take the balance with it — is worth
 * testing without a campus network or a card session.
 */
interface SchoolCardDataSource {
    /**
     * The last successful card as of [today], or null when nothing has ever been cached.
     *
     * [today] decides whether the cached *day's* totals are still true; the balance comes back either
     * way, because the two parts of a card age differently.
     */
    fun getCachedCard(today: LocalDate): FetchResult<SchoolCardSnapshot>?

    /**
     * Fetches the card's state, falling back to [getCachedCard] when the network, the login or the
     * server fails. Throws only when there is no cached balance to fall back to.
     */
    suspend fun getSchoolCard(): FetchResult<SchoolCardSnapshot>
}

/**
 * The campus card system's session: the OAuth hop, the balance, and the day's transactions.
 *
 * Ported from `lib/repository/ids_session/school_card_session.dart`. One part of that flow is unusual,
 * and it is why this is not four lines: **the session is entered through the OAuth landing page, not a
 * request.** The card system hands out its handle as a hidden `openid` input on the page IDS redirects
 * to, and every later call has to carry it.
 *
 * 1. ask IDS for a ticket for the card system's **registered** CAS service — the campus app's CAS entry
 *    point for the card, built by [SchoolCardEndpoints.CAS_LOGIN_TARGET] — and walk the redirect chain
 *    to its end;
 * 2. read `openid` off the address that chain ended on, falling back to the hidden field on the page —
 *    that is the card session, and it is kept for [OPEN_ID_VALIDITY], as the original kept it;
 * 3. read the balance from `openMyAccount?openid=…`, which answers with HTML;
 * 4. read today's transactions from `queryCardSelfTradeList`, which answers with JSON.
 *
 * Using the card system's own OAuth entry point as the service — which is what the original did, and
 * what this port did until the live server was measured — makes IDS answer 200 with its
 * 「应用未注册」 page, because a redirector is not a registered application. See
 * [SchoolCardEndpoints.CAS_LOGIN_TARGET].
 *
 * The original retried **once** after a failure with a freshly minted `openid`, on the theory that a
 * stale handle rather than a broken request is the likely cause. That retry is kept — including its
 * cost, a second login — but its staleness check is not: there, `forceRefresh` re-entered the same
 * five-minute validity test it was meant to bypass, so the "refresh" handed back the cached handle and
 * the retry repeated the identical request. Handling that correctly is the difference between a retry
 * and a duplicate.
 *
 * @param captcha the solver used for the IDS login step, as everywhere else in this app.
 * @param clock and [timeZone] are injectable so "today" is not the day the test runs.
 *
 * Internal because it takes an [SchoolCardApi], which speaks in wire rows. The rest of the app binds
 * [SchoolCardDataSource].
 */
internal class SchoolCardSession(
    private val ids: IdsSessionRepository,
    private val api: SchoolCardApi,
    private val cache: SchoolCardCache,
    private val captcha: SliderCaptchaSolver = NoCaptchaSolver,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : SchoolCardDataSource {

    private val openIdGuard = Mutex()
    private var openId: String? = null
    private var openIdFetchedAt: Instant? = null

    override fun getCachedCard(today: LocalDate): FetchResult<SchoolCardSnapshot>? =
        cache.read(today)

    /**
     * Reads the card, with the original's fallback: a failure never throws away a cached balance, it
     * annotates it with why the refresh failed.
     *
     * Cancellation is the one exception, as in the energy module: turning a cancelled refresh into
     * "here is the cached value" would show stale data as if the request had completed.
     */
    override suspend fun getSchoolCard(): FetchResult<SchoolCardSnapshot> {
        val fetchTime = clock.now()
        val today = clock.now().toLocalDateTime(timeZone).date
        val cached = cache.read(today)

        return try {
            val snapshot = requestSchoolCard()
            // Stored with the day its totals describe, so a launch tomorrow keeps the balance and drops
            // the spending figure rather than mislabelling yesterday's.
            cache.save(snapshot, knownDate = today)
            FetchResult.fresh(fetchTime = fetchTime, data = snapshot)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            appLog.w(error) { "[SchoolCardSession] Could not refresh the campus card" }
            if (cached == null) throw error
            FetchResult.cache(
                fetchTime = cached.fetchTime,
                data = cached.data,
                hintKey = schoolCardCacheHintFor(error).key,
            )
        }
    }

    /**
     * One attempt at the whole card, under a freshly checked handle.
     *
     * The two reads are independent on purpose: a transaction query that fails still leaves a balance
     * worth showing, and the original's own card page and home page each did only one of them. They
     * are *run* independently but *reported* together, which is what makes [withOpenIdRetry] able to
     * act on either: a failure swallowed here would be a retry that never happened.
     */
    private suspend fun requestSchoolCard(): SchoolCardSnapshot = withOpenIdRetry { handle ->
        val today = clock.now().toLocalDateTime(timeZone).date
        val balance = balanceOrNull(handle)
        val trades = tradeListOrNull(handle, today)

        // A stale handle is the failure the retry exists for, and the two requests fail with different
        // exception types; the first failure is the one reported, because the second is usually its
        // consequence.
        trades.exceptionOrNull()?.let { throw it }
        balance.exceptionOrNull()?.let { throw it }

        if (balance.getOrNull() == null && trades.getOrNull() == null) {
            throw SchoolCardProtocolException("校园卡余额与流水均未取到")
        }

        val snapshot = trades.getOrNull()?.let { summarise(it, today) }
        SchoolCardSnapshot(
            balance = balance.getOrNull(),
            // Null, not zero, when the query failed: the tile must be able to say it does not know,
            // because a zero under the heading 今日支出 is a claim about a day nobody read.
            todayExpenseCents = snapshot?.todayExpenseCents,
            todayIncomeCents = snapshot?.todayIncomeCents ?: 0,
        )
    }

    /** The balance, or the reason it could not be read. */
    private suspend fun balanceOrNull(handle: String): Result<String?> = try {
        val html = api.fetchAccountPage(handle)
        val balance = parseBalance(html)
        // Whether the account page is a real one decides how to read everything after it: a session
        // that was never established serves *some* page at 200, and every later call is then refused for
        // a reason that has nothing to do with the call.
        appLog.i {
            "[SchoolCardSession] Balance read: ${balance ?: "no amount found"}, " +
                    "pageLooksLikeAnError=${html.contains("页面丢失") || html.contains("资源受限")}, " +
                    "page=${html.replace(Regex("\\s+"), " ").take(160)}"
        }
        if (balance == null) appLog.w { "[SchoolCardSession] The account page carried no balance" }
        Result.success(balance)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        appLog.w(error) { "[SchoolCardSession] Could not read the balance" }
        Result.failure(error)
    }

    /** The day's transactions, or the reason they could not be read. */
    private suspend fun tradeListOrNull(
        handle: String,
        today: LocalDate,
    ): Result<List<SchoolCardRecord>> = try {
        Result.success(api.queryTradeList(handle, today.toCardQueryDate(), today.toCardQueryDate()))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        appLog.w(error) { "[SchoolCardSession] Could not read today's transactions" }
        Result.failure(error)
    }

    private fun summarise(records: List<SchoolCardRecord>, today: LocalDate) = SchoolCardSnapshot(
        balance = null,
        todayExpenseCents = todayExpenseCents(records, today),
        todayIncomeCents = todayIncomeCents(records, today),
    )

    /**
     * Runs [action] with the card's handle, and once more with a new one if it fails.
     *
     * The second attempt is the original's own policy, kept because the failure it guards against is
     * real: the handle outlives the cookie that makes it meaningful, and a request carrying a stale one
     * is refused indistinguishably from a request that is simply wrong.
     */
    private suspend fun <T> withOpenIdRetry(action: suspend (String) -> T): T {
        val handle = openId()
        return try {
            action(handle)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            appLog.w(error) { "[SchoolCardSession] Request failed, retrying with a new openid" }
            action(openId(force = true))
        }
    }

    /**
     * The card handle, minted once and reused while it is fresh.
     *
     * Concurrent callers share one login rather than racing to start two — the guard is what makes that
     * true, and it is why the original wrapped this in a `SingleFlight` as well.
     */
    private suspend fun openId(force: Boolean = false): String = openIdGuard.withLock {
        val current = openId
        if (!force && current != null && isFresh()) return@withLock current

        return@withLock mintOpenId().also {
            openId = it
            openIdFetchedAt = clock.now()
        }
    }

    /**
     * Whether the handle is still inside [OPEN_ID_VALIDITY].
     *
     * A handle is also dropped outright on failure by [withOpenIdRetry]'s `force`, so this is the only
     * staleness rule — and unlike the original's, it is one a forced refresh actually bypasses.
     */
    private fun isFresh(): Boolean {
        val fetchedAt = openIdFetchedAt ?: return false
        return clock.now() - fetchedAt < OPEN_ID_VALIDITY
    }

    /** Logs in for the card service and reads the handle off the page the chain ended on. */
    private suspend fun mintOpenId(): String {
        val target = SchoolCardEndpoints.CAS_LOGIN_TARGET
        val location = try {
            ids.loginForService(service = target, captcha = captcha).getOrThrow()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (auth: IdsAuthException) {
            // The IDS layer classified this itself: a wrong password, a refused captcha, an
            // unanswered second factor. Those are the errors worth naming in the UI.
            throw auth
        } catch (error: Throwable) {
            throw SchoolCardNetworkException("校园卡登录请求失败", error)
        }

        val response = try {
            ids.followRedirects(initialLocation = location, service = target)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (auth: IdsAuthException) {
            throw auth
        } catch (error: Throwable) {
            throw SchoolCardNetworkException("校园卡登录跳转失败", error)
        }

        // Read from the response the chain *ended* on rather than re-fetched: that response is the
        // card system's own callback, it already carries the handle, and asking again would be a second
        // round trip — and, since walking the chain is what establishes the session, a second login.
        val finalUrl = response.call.request.url.toString()
        val html = response.bodyAsText()

        val handle = api.extractOpenIdFromUrl(finalUrl) ?: api.extractOpenId(html)
        // Every later call claims this address as its `Referer`: the card system wants browser-shaped
        // requests, and the page the client is "on" is the one the chain landed on.
        api.onLandedAt(finalUrl)
        appLog.i {
            "[SchoolCardSession] Login chain ended at HTTP ${response.status.value}, " +
                    "address=$finalUrl, " +
                    "handle=${handle?.let { "${it.length} chars, ${it.take(6)}…" } ?: "missing"}"
        }

        // The address is the authority on the handle. A body is only consulted when the address carries
        // none, and it is a weak source: the card system renders an `openid` input on its *error* pages
        // too — a placeholder the endpoints refuse — so a handle read from a page is logged with the page
        // it came from rather than trusted silently.
        if (handle != null && api.extractOpenIdFromUrl(finalUrl) == null) {
            appLog.w {
                "[SchoolCardSession] The handle came from the page body, not the address; " +
                        "the endpoints refuse a placeholder read this way (302 to errorPage)"
            }
        }

        return handle ?: throw SchoolCardProtocolException(
            "校园卡登录链未返回 openid（最终地址：$finalUrl，HTTP ${response.status.value}）",
        )
    }

    private companion object {
        /** How long the card handle is trusted, as in the original. */
        val OPEN_ID_VALIDITY: Duration = Duration.parse("5m")
    }
}

/**
 * Classifies a refresh failure into the hint shown beside a cached balance.
 *
 * The same mapping the energy module makes, by what went wrong rather than by exception name: the
 * original's own exception types do not all exist here, because its HTTP layer was replaced by Ktor.
 */
internal fun schoolCardCacheHintFor(error: Throwable): SchoolCardCacheHint = when (error) {
    is MissingCredentialsException -> SchoolCardCacheHint.ACCOUNT_MISSING
    is CaptchaSolveFailedException -> SchoolCardCacheHint.CAPTCHA_FAILED
    is PasswordWrongException -> SchoolCardCacheHint.PASSWORD_WRONG
    is SchoolCardProtocolException -> SchoolCardCacheHint.ACCOUNT_PARSE_FAILED
    is SchoolCardNetworkException -> SchoolCardCacheHint.NETWORK_FAILED
    // Ordered after the specific auth failures: all of them are IdsAuthException subtypes.
    is IdsAuthException -> SchoolCardCacheHint.LOGIN_FAILED
    else -> SchoolCardCacheHint.UNKNOWN_ERROR
}
