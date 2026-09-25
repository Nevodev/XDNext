package com.nevoit.xdnext.data.schoolcard

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Request- and response-shape tests for the campus card's HTTP surface.
 *
 * The body shape comes from `lib/repository/ids_session/school_card_session.dart`, which is the only
 * record of this protocol — there is no probe for it, unlike the energy system, whose wire bytes were
 * captured by running the reference's own client. What *is* measured here is everything that can be:
 * the field names and the endpoint, and the parsing of both an answer and a page that does not match.
 */
class SchoolCardApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    // --- the trade query ------------------------------------------------------------------------

    @Test
    fun postsTheRangeAndTheHandle() = runTest {
        val (api, requests) = api({ """{"resultData":[]}""" })

        api.queryTradeList("HANDLE", from = "2026-03-05", to = "2026-03-05")

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        // The handle travels in the query string *and* in the body, which is what the original sent.
        assertEquals(
            "https://v8scan.xidian.edu.cn/selftrade/queryCardSelfTradeList?openid=HANDLE",
            request.url.toString(),
        )
        assertEquals(
            """{"beginDate":"2026-03-05","endDate":"2026-03-05","tradeType":"-1","openid":"HANDLE"}""",
            request.bodyText(),
            "`tradeType` is the original's own \"-1\", and all four fields are sent.",
        )
    }

    @Test
    fun marksTheRequestAsAnAjaxCall() = runTest {
        // Measured against the live server, and the reason the query looked broken for so long: without
        // `X-Requested-With`, the card system's session manager answers
        // `302 → /errorPage?message=资源受限，页面丢失!` *before* the controller runs — whatever the
        // handle. With it, the controller's own JSON verdict comes back. The reference app sets neither
        // header, so reproducing it exactly is what fails here.
        val (api, requests) = api({ """{"resultData":[]}""" })

        api.queryTradeList("HANDLE", "2026-03-05", "2026-03-05")

        val request = requests.single()
        assertEquals("XMLHttpRequest", request.headers["X-Requested-With"])
        assertEquals("https://v8scan.xidian.edu.cn/home/openHomePage", request.headers["Referer"])
    }

    @Test
    fun reportsTheEndpointsOwnRefusalInsteadOfAnEmptyDay() = runTest {
        // A refusal arrives as HTTP 200 with `success: false`, so the status is not the verdict — and an
        // empty list here would read as "you spent nothing today", which is a wrong answer rather than a
        // missing one.
        val (api, _) = api({ """{"message":"openid无效，页面丢失!","success":false}""" })

        val error = assertFailsWith<SchoolCardProtocolException> {
            api.queryTradeList("HANDLE", "2026-03-05", "2026-03-05")
        }
        assertTrue(
            error.message!!.contains("openid无效"),
            "The endpoint's own words are the message."
        )
    }

    @Test
    fun reportsARefusedHandleOnTheAccountPageToo() = runTest {
        // Both endpoints refuse the same way, so the account page must not hand a refusal to the scraper
        // as if it were markup: the scraper would find no amount and the failure would be reported as a
        // changed page rather than as the refusal it is.
        val (api, _) = api({ """{"message":"openid无效，页面丢失!","success":false}""" })

        val error = assertFailsWith<SchoolCardProtocolException> { api.fetchAccountPage("HANDLE") }
        assertTrue(error.message!!.contains("openid无效"))
    }

    @Test
    fun claimsTheAddressTheSessionWasEstablishedAt() = runTest {
        // The card system wants browser-shaped requests, and a `Referer` naming a page the session was
        // not established through is the mismatch such a check exists to catch.
        val (api, requests) = api({ """{"resultData":[]}""" })

        api.onLandedAt("https://v8scan.xidian.edu.cn/home/openHomePage?openid=HANDLE")
        api.queryTradeList("HANDLE", "2026-03-05", "2026-03-05")

        assertEquals(
            "https://v8scan.xidian.edu.cn/home/openHomePage?openid=HANDLE",
            requests.single().headers["Referer"],
        )
    }

    @Test
    fun readsTheRowsItRecognisesAndSkipsTheOnesItDoesNot() = runTest {
        val (api, _) = api(
            {
                """
                {"resultData":[
                  {"mername":"食堂","txdate":"2026-03-05","txamt":"-12.30"},
                  {"mername":"浴室","txdate":"2026-03-05"},
                  {"mername":"超市","txdate":"2026-03-05","txamt":-5}
                ]}
                """
            },
        )

        val records = api.queryTradeList("HANDLE", "2026-03-05", "2026-03-05")

        assertEquals(2, records.size, "The row with no amount is dropped rather than read as zero.")
        assertEquals(listOf("食堂", "超市"), records.map { it.merchant })
        // Amounts are carried as the wire wrote them; the sum is what turns them into cents.
        assertEquals(listOf("-12.30", "-5"), records.map { it.amount })
    }

    @Test
    fun treatsAnEmptyOrAbsentResultListAsNoTransactions() = runTest {
        val (empty, _) = api({ """{"resultData":[]}""" })
        val (absent, _) = api({ """{"code":"0"}""" })

        assertTrue(empty.queryTradeList("HANDLE", "2026-03-05", "2026-03-05").isEmpty())
        assertTrue(absent.queryTradeList("HANDLE", "2026-03-05", "2026-03-05").isEmpty())
    }

    @Test
    fun reportsANonJsonAnswer() = runTest {
        val (api, _) = api(html = "<html><body>登录已过期</body></html>")

        assertFailsWith<SchoolCardProtocolException> {
            api.queryTradeList("HANDLE", "2026-03-05", "2026-03-05")
        }
    }

    @Test
    fun reportsATransportFailureAsANetworkFailure() = runTest {
        // Standing on a phone with no campus network is the ordinary case, and the cache hint needs to
        // name it rather than let an engine-specific exception through.
        val engine = MockEngine { throw IOException("network is unreachable") }
        val api = SchoolCardApi(client(engine), json)

        assertFailsWith<SchoolCardNetworkException> {
            api.queryTradeList("HANDLE", "2026-03-05", "2026-03-05")
        }
    }

    // --- the handle and the balance -------------------------------------------------------------

    @Test
    fun readsTheHandleFromTheAddressTheChainEndedOn() {
        // The chain's last hop back into the card system carries the handle, and the page it renders
        // may be an error page that still names it — which is why the address is read first.
        val api = SchoolCardApi(client(MockEngine { respond("") }), json)

        assertEquals(
            "DCFDE1134D316C1242D25B6EAB6B41A3",
            api.extractOpenIdFromUrl(
                "https://v8scan.xidian.edu.cn/home/openHomePage?openid=DCFDE1134D316C1242D25B6EAB6B41A3",
            ),
        )
        assertNull(api.extractOpenIdFromUrl("https://v8scan.xidian.edu.cn/home/openHomePage"))
        assertNull(api.extractOpenIdFromUrl("https://v8scan.xidian.edu.cn/home/openHomePage?openid="))
    }

    @Test
    fun readsTheHandleFromTheLandingPagesHiddenField() {
        val api = SchoolCardApi(client(MockEngine { respond("") }), json)

        assertEquals(
            "abc123",
            api.extractOpenId(
                """<form><input type="hidden" id="openid" value="abc123"><input id="other"></form>""",
            ),
        )
        // The original also demanded `type="hidden"`; the id is what identifies the field, and a page
        // that keeps the id and drops the type is still the page.
        assertEquals(
            "abc123",
            api.extractOpenId("""<input id="openid" value="abc123">"""),
        )
    }

    @Test
    fun reportsAMissingHandleRatherThanAnEmptyString() {
        val api = SchoolCardApi(client(MockEngine { respond("") }), json)

        assertNull(api.extractOpenId("""<input id="openid" value="">"""))
        assertNull(api.extractOpenId("<html><body>统一认证</body></html>"))
    }

    @Test
    fun readsTheBalanceFromTheAccountsListMarkup() {
        // The original's positional path: the first `<li>`'s second child's second child.
        val html = """
            <ul>
              <li><span>姓名</span><div><span>学号</span><span>25.60</span></div></li>
            </ul>
        """

        assertEquals("25.60", parseBalance(html))
    }

    @Test
    fun stripsTheCurrencyDecorationFromTheBalance() {
        val html = """
            <ul>
              <li><span>余额</span><div><span>单位</span><span>¥125.00 元</span></div></li>
            </ul>
        """

        assertEquals("125.00", parseBalance(html))
    }

    @Test
    fun fallsBackToTheFirstAmountOnARestructuredPage() {
        // A page that no longer matches positionally still has the number on it; a balance is worth
        // reading from an unexpected place, while a wrong cell is not — hence the positional path
        // first and the log line when it falls through.
        val html = """<div class="card">校园卡余额：<b>18</b> 元</div>"""

        assertEquals("18", parseBalance(html))
    }

    @Test
    fun reportsAPageWithNoAmountAtAll() {
        assertNull(parseBalance("<html><body>系统维护中</body></html>"))
        assertNull(parseBalance(""))
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun HttpRequestData.bodyText(): String? = (body as? TextContent)?.text

    private fun client(engine: MockEngine) = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(this@SchoolCardApiTest.json) }
    }

    /** A [SchoolCardApi] whose engine answers with [handler]'s body and records every request. */
    private fun api(
        handler: (HttpRequestData) -> String = { """{"resultData":[]}""" },
        html: String? = null,
    ): Pair<SchoolCardApi, MutableList<HttpRequestData>> {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            respond(
                content = html ?: handler(request),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()),
            )
        }
        return SchoolCardApi(client(engine), json) to requests
    }
}
