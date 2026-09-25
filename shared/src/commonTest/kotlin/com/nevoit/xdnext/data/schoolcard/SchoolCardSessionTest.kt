package com.nevoit.xdnext.data.schoolcard

import com.nevoit.xdnext.core.crypto.AesCbc
import com.nevoit.xdnext.core.store.InMemorySecureStore
import com.nevoit.xdnext.core.store.InMemorySettings
import com.nevoit.xdnext.core.store.SettingsStore
import com.nevoit.xdnext.data.ids.IdsAuthApi
import com.nevoit.xdnext.data.ids.IdsCrypto
import com.nevoit.xdnext.data.ids.IdsEndpoints
import com.nevoit.xdnext.data.ids.NoCaptchaSolver
import com.nevoit.xdnext.data.net.FileCookieStorage
import com.nevoit.xdnext.data.session.CredentialStore
import com.nevoit.xdnext.data.session.IdsSessionRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests for the campus card session's own logic: the handle, the retry, and the two-read policy.
 *
 * The session is driven through a **real [IdsSessionRepository]** on a mock engine rather than through a
 * stubbed one, because the part that is easy to get wrong is where its requests go: the handle lives on
 * the address the IDS redirect chain *ends* on, and a session that re-fetched that page instead of
 * reading the response in hand would be a second login. Pinning that means letting the real redirect
 * walker run.
 *
 * The two departures from the original are both asserted here, because both are behaviour rather than
 * style:
 *
 *  - a forced handle refresh really does mint a new handle — the original's `forceRefresh` re-entered the
 *    same validity window it was meant to bypass, so its retry repeated the identical request;
 *  - the day's spending is queried for **today**, from the injected clock, rather than for the whole
 *    month the original's card page defaulted to.
 */
class SchoolCardSessionTest {

    // --- the happy path -------------------------------------------------------------------------

    @Test
    fun readsTheBalanceAndTodaysSpending() = runTest {
        val fixture = Fixture()
        fixture.tradeList(
            """
            [
              {"mername":"食堂","txdate":"2026-03-05","txamt":"-12.30"},
              {"mername":"超市","txdate":"2026-03-05","txamt":"-7.70"},
              {"mername":"圈存","txdate":"2026-03-05","txamt":"200.00"},
              {"mername":"食堂","txdate":"2026-03-04","txamt":"-30.00"}
            ]
            """,
        )
        fixture.balance(text = "25.60")

        val result = fixture.session.getSchoolCard()

        assertEquals("25.60", result.data.balance)
        assertEquals(
            2000L,
            result.data.todayExpenseCents,
            "Yesterday's 30 and today's top-up stay out."
        )
        assertEquals(20000L, result.data.todayIncomeCents)
    }

    @Test
    fun asksForTodayRatherThanTheMonthTheOriginalDefaultedTo() = runTest {
        // The original's card page opened on the first of the month and summed whatever came back, which
        // is a month-to-date figure under a heading that said "合计". This tile says 今日支出, so the
        // query has to be the day.
        val fixture = Fixture()
        fixture.tradeList("[]")

        fixture.session.getSchoolCard()

        assertEquals(
            """{"beginDate":"2026-03-05","endDate":"2026-03-05","tradeType":"-1","openid":"OPENID-1"}""",
            fixture.tradeRequestBody(),
        )
    }

    @Test
    fun reusesTheHandleForLaterReads() = runTest {
        val fixture = Fixture()
        fixture.tradeList("[]")

        fixture.session.getSchoolCard()
        fixture.session.getSchoolCard()

        assertEquals(1, fixture.oauthHops(), "A fresh handle means a fresh login, so it is reused.")
    }

    // --- failures -------------------------------------------------------------------------------

    @Test
    fun fallsBackToTheCachedBalanceWhenNothingCanBeRead() = runTest {
        val fixture = Fixture()
        fixture.cache.save(
            SchoolCardSnapshot(balance = "88.00", todayExpenseCents = null, todayIncomeCents = 0),
            knownDate = null,
        )
        fixture.failTradeListOnAttempts = setOf(1, 2)
        fixture.balance(text = "系统维护中")

        val result = fixture.session.getSchoolCard()

        assertTrue(result.isCache)
        assertEquals("88.00", result.data.balance)
        assertNull(
            result.data.todayExpenseCents,
            "The totals were never read, so there is nothing to show — not a zero.",
        )
        assertEquals(SchoolCardCacheHint.NETWORK_FAILED.key, result.hintKey)
    }

    @Test
    fun servesTodaysSpendingFromTheCacheWhenTheRefreshFails() = runTest {
        // The point of caching the totals: a refresh that fails after one that worked must not blank the
        // day's figure, as long as the day is still the same one.
        val fixture = Fixture()
        fixture.cache.save(
            SchoolCardSnapshot(balance = "88.00", todayExpenseCents = 1230, todayIncomeCents = 0),
            knownDate = LocalDate(2026, 3, 5),
        )
        fixture.failTradeListOnAttempts = setOf(1, 2)
        fixture.balance(text = "系统维护中")

        val result = fixture.session.getSchoolCard()

        assertTrue(result.isCache)
        assertEquals("88.00", result.data.balance)
        assertEquals(1230L, result.data.todayExpenseCents)
    }

    @Test
    fun aFailedTradeQueryDoesNotTakeTheBalanceWithIt() = runTest {
        val fixture = Fixture()
        fixture.balance(text = "25.60")
        fixture.failTradeListOnAttempts = setOf(1, 2)

        val result = fixture.session.getSchoolCard()

        assertEquals("25.60", result.data.balance)
        assertNull(result.data.todayExpenseCents)
    }

    @Test
    fun aFailedBalanceReadDoesNotTakeTheTradeListWithIt() = runTest {
        val fixture = Fixture()
        fixture.balance(text = "系统维护中")
        fixture.tradeList("""[{"mername":"食堂","txdate":"2026-03-05","txamt":"-12.30"}]""")

        val result = fixture.session.getSchoolCard()

        assertNull(result.data.balance)
        assertEquals(1230L, result.data.todayExpenseCents)
    }

    @Test
    fun throwsWhenThereIsNothingAtAllToShowAndNothingCached() = runTest {
        val fixture = Fixture()
        // The trade query fails on both the attempt and the retry, so no amount is read at all —
        // otherwise a retry would look like a working card.
        fixture.failTradeListOnAttempts = setOf(1, 2)
        fixture.balance(text = "系统维护中")

        val error = assertFailsWith<Throwable> { fixture.session.getSchoolCard() }

        assertEquals(
            SchoolCardCacheHint.NETWORK_FAILED,
            schoolCardCacheHintFor(error),
            "With nothing to show, the failure is reported rather than dressed up as a balance.",
        )
    }

    @Test
    fun retriesOnceWithANewHandle() = runTest {
        // The retry only means something if the second attempt carries a *different* handle: the
        // original's did not, so it repeated a request that had already been refused.
        val fixture = Fixture()
        fixture.tradeList("""[{"mername":"食堂","txdate":"2026-03-05","txamt":"-12.30"}]""")
        fixture.rejectTradeListOnAttempts = setOf(1)

        val result = fixture.session.getSchoolCard()

        assertEquals(1230L, result.data.todayExpenseCents)
        assertEquals(2, fixture.tradeRequests().size, "One attempt, then one retry.")
        assertEquals("OPENID-1", fixture.tradeRequests().first().openId())
        assertEquals(
            "OPENID-2",
            fixture.tradeRequests().last().openId(),
            "The retry must carry a *new* handle; the original's handed back the cached one.",
        )
        assertEquals(
            2,
            fixture.oauthHops(),
            "Each handle costs a login, and the retry needs a new one."
        )
    }

    // --- the handle itself ----------------------------------------------------------------------

    @Test
    fun reportsMissingCredentialsAsAMissingAccountRatherThanANetworkFailure() = runTest {
        val fixture = Fixture(storedCredentials = false)

        val error = assertFailsWith<Throwable> { fixture.session.getSchoolCard() }

        assertEquals(SchoolCardCacheHint.ACCOUNT_MISSING, schoolCardCacheHintFor(error))
    }

    // --- fixture --------------------------------------------------------------------------------

    /**
     * The whole stack on a mock engine: the real IDS session, the real card API, and a real cache file
     * under a temporary directory.
     */
    private class Fixture(storedCredentials: Boolean = true) {

        val cache: SchoolCardCache
        val session: SchoolCardSession

        /** The trade list the engine answers with; `[]` unless a test says otherwise. */
        var tradeListBody: String = "[]"

        /** The account page's markup. */
        var balanceBody: String =
            "<ul><li><span>余额</span><div><span>单位</span><span>25.60</span></div></li></ul>"

        /**
         * Which trade attempts fail, counting from 1.
         *
         * Targeted rather than a plain flag because the session retries once: a test that wants "the
         * query never works" has to fail both attempts, and a test that wants "the first attempt is
         * refused" has to fail exactly one. A flag that failed everything would make the retry invisible,
         * which is the one thing these tests exist to see.
         */
        var failTradeListOnAttempts: Set<Int> = emptySet()

        /** Which trade attempts are answered with an HTTP error rather than a transport failure. */
        var rejectTradeListOnAttempts: Set<Int> = emptySet()

        /** When set, the account page request fails at the transport layer instead of answering. */
        var failBalance = false

        private val requests = mutableListOf<HttpRequestData>()
        private var tradeAttempts = 0

        /** How many handles have been minted, which is one per login. */
        private var handleCount = 0

        /** How many times the chain reached the card system's callback. */
        private var oauthHops = 0

        init {
            val instance = instances++
            val directory =
                FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "xdnext-school-card-test-$instance"
            cache = SchoolCardCache(FileSystem.SYSTEM, directory, JSON)

            val engine = MockEngine { request ->
                requests += request
                val url = request.url.toString()
                when {
                    // The card system's callback: the chain's last hop, carrying the handle in the
                    // address. Its body deliberately holds *no* handle, so a session that read only the
                    // page — which is what the original did — would fail this fixture.
                    url.startsWith(SchoolCardEndpoints.OAUTH_CALLBACK) -> {
                        oauthHops++
                        respond(
                            content = "<html><body>校园卡</body></html>",
                            status = HttpStatusCode.OK,
                        )
                    }

                    // The campus app's OAuth endpoint, which the login hands a ticket to. Minting the
                    // handle here is what makes a retry observably different from a repeat.
                    url.startsWith(SchoolCardEndpoints.CAMPUS_APP) -> {
                        handleCount++
                        respond(
                            content = "",
                            status = HttpStatusCode.Found,
                            headers = headersOf(
                                HttpHeaders.Location,
                                "${SchoolCardEndpoints.OAUTH_CALLBACK}?openid=OPENID-$handleCount",
                            ),
                        )
                    }

                    // A warm session: IDS redirects straight to the service, which is what a restored
                    // cookie does. The walker then follows the chain to the callback.
                    url.startsWith(IdsEndpoints.LOGIN) ->
                        respond(
                            content = "",
                            status = HttpStatusCode.Found,
                            headers = headersOf(
                                HttpHeaders.Location,
                                SchoolCardEndpoints.CAS_LOGIN_TARGET,
                            ),
                        )

                    url.startsWith(SchoolCardEndpoints.MY_ACCOUNT) ->
                        if (failBalance) {
                            throw IOException("balance request failed")
                        } else {
                            respond(balanceBody, HttpStatusCode.OK)
                        }

                    url.startsWith(SchoolCardEndpoints.SELF_TRADE_LIST) -> {
                        tradeAttempts++
                        when {
                            tradeAttempts in failTradeListOnAttempts ->
                                throw IOException("trade request failed")

                            tradeAttempts in rejectTradeListOnAttempts ->
                                respond("", HttpStatusCode.InternalServerError)

                            else -> respond(
                                content = """{"resultData":$tradeListBody}""",
                                status = HttpStatusCode.OK,
                                headers = headersOf(
                                    HttpHeaders.ContentType,
                                    ContentType.Application.Json.toString(),
                                ),
                            )
                        }
                    }

                    else -> respond("", HttpStatusCode.NotFound)
                }
            }

            // The card API serialises its request bodies through the client's ContentNegotiation, so a
            // client without it cannot post the trade query at all. Redirects are deliberately *not*
            // followed, exactly as the production IDS client is configured: the CAS flow is a chain this
            // app walks by hand, and letting the engine follow it would hide the hop the handle is read
            // from — and would silently make every request cost two.
            val client = HttpClient(engine) {
                followRedirects = false
                expectSuccess = false
                install(ContentNegotiation) { json(JSON) }
            }
            val json = JSON
            val ids = buildIdsSession(client, json, storedCredentials, instance)
            session = SchoolCardSession(
                ids = ids,
                api = SchoolCardApi(client, json),
                cache = cache,
                captcha = NoCaptchaSolver,
                clock = FixedClock(LocalDate(2026, 3, 5)),
                timeZone = TimeZone.UTC,
            )
        }

        fun balance(text: String) {
            balanceBody =
                "<ul><li><span>余额</span><div><span>单位</span><span>$text</span></div></li></ul>"
        }

        fun tradeList(body: String) {
            tradeListBody = body
        }

        fun tradeRequests(): List<HttpRequestData> = requests.filter {
            it.url.toString().startsWith(SchoolCardEndpoints.SELF_TRADE_LIST)
        }

        fun tradeRequestBody(): String =
            (tradeRequests().last().body as TextContent).text

        fun oauthHops(): Int = oauthHops
    }
}

/**
 * The real IDS session, with credentials already stored and the cookie jar in a temporary directory.
 *
 * A mock engine can exercise the whole automatic path because the login page answers with a 3xx, which is
 * exactly what a device the server still trusts gets. A file-level function because the fixture is a
 * nested class, which cannot reach the test class's own members.
 */
private fun buildIdsSession(
    client: HttpClient,
    json: Json,
    storedCredentials: Boolean,
    instance: Int,
): IdsSessionRepository {
    val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "xdnext-school-card-cookies-$instance"
    val cookies = FileCookieStorage(FileSystem.SYSTEM, directory / "ids.json", json)
    val crypto = IdsCrypto(AesCbc())
    val settings = SettingsStore(InMemorySettings())
    val credentials = CredentialStore(InMemorySecureStore(), settings, crypto)

    if (storedCredentials) {
        runBlocking { credentials.save("20210001", "secret") }
    }
    return IdsSessionRepository(
        api = IdsAuthApi(client, cookies, json),
        idsCrypto = crypto,
        credentials = credentials,
    )
}

/** A clock pinned to one day, so "today" is not the day the test runs. */
private class FixedClock(private val date: LocalDate) : Clock {
    override fun now(): Instant =
        LocalDateTime(date.year, date.month, date.day, 12, 0).toInstant(TimeZone.UTC)
}

/**
 * The handle a trade request carried.
 *
 * Read out of the **body**, not the query string: that is where this protocol puts it, and a test that
 * looked at the URL would find nothing there and pass vacuously.
 */
private fun HttpRequestData.openId(): String? =
    (body as? TextContent)
        ?.text
        ?.let { runCatching { JSON.parseToJsonElement(it).jsonObject["openid"] }.getOrNull() }
        ?.jsonPrimitive
        ?.content

private val JSON = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

/** Distinguishes the fixtures' temporary directories, so no two tests share a file. */
private var instances = 0
