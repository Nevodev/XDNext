package com.nevoit.xdnext.data.experiment

import com.fleeksoft.charset.Charsets
import com.fleeksoft.charset.toByteArray
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.IOException

/**
 * Request- and response-shape tests for the lab site's HTTP surface.
 *
 * The shapes come from `physics_experiment_session.dart`, which is the only record of this protocol —
 * there is no probe for it, unlike the energy system, whose wire bytes were captured by running the
 * reference's own client. What *is* measurable here is measured: the endpoint, the field names of the
 * login postback, the cookie header built from the answer, the page's own charset, and the fields a
 * course switch has to carry back.
 */
class ExperimentScheduleApiTest {

    @Test
    fun logsInWithTheStudentsNumberAndTheExperimentPassword() = runTest {
        val (api, requests) = api { loginAccepted() }

        api.login(account = "20210001", password = "secret")

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("http://wlsy.xidian.edu.cn/PhyEws/default.aspx", request.url.toString())
        val body = request.bodyText()
        assertTrue(
            body.contains(
                "login1%24StuLoginID=20210001&login1%24StuPassword=secret&login1%24UserRole=Student",
            ),
            "The two credentials and the student role are the postback's own field names: $body",
        )
        assertTrue(
            body.contains("__VIEWSTATE=%2FwEPDwUKMTEzNzM0MjM0OWQYAQUeX19D"),
            "The view state the original sent is still sent, and only encoded once.",
        )
        assertTrue(body.contains("__EVENTVALIDATION=%2FwEdAAcKecdPGDB%2BfW8Tyghx"))
    }

    @Test
    fun escapesTheCredentialsRatherThanSplicingThemIntoTheBody() = runTest {
        // The original interpolated both values into a raw body, so a password containing `&` arrived as
        // fields the server never saw. The value is a form value, so it is encoded like one.
        val (api, requests) = api { loginAccepted() }

        api.login(account = "20210001", password = "a&b=c")

        val body = requests.single().bodyText()
        assertTrue(body.contains("login1%24StuPassword=a%26b%3Dc"), "Got: $body")
    }

    @Test
    fun readsTheCookieHeaderTheLoginAnsweredWith() = runTest {
        val (api, _) = api { loginAccepted() }

        val cookie = api.login("20210001", "secret")

        assertTrue(
            cookie.contains("PhyEws_StuName=waterfloatinggenderly"),
            "The student-name cookie is the placeholder value the original sent: $cookie",
        )
        assertTrue(cookie.contains(".ASPXAUTH=xyz"), "The auth cookie is kept.")
        assertFalse(
            cookie.contains("ASP.NET_SessionId"),
            "An HttpOnly cookie is dropped, as the original's own filter dropped it: $cookie",
        )
    }

    @Test
    fun reportsTheSitesOwnRefusalInsteadOfAnEmptyList() = runTest {
        // A refused login answers 200 with the login page again, so the status is not the verdict and the
        // body is the only place the reason exists.
        val (api, _) = api {
            respond(
                content = gb(
                    "<html><body><span id=\"login1_Label1\">" +
                            "<font color=\"Red\">用户名或密码错误<br></font></span></body></html>",
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=gb2312"),
            )
        }

        val error = assertFailsWith<ExperimentLoginFailedException> { api.login("20210001", "wrong") }
        assertEquals("用户名或密码错误。", error.detail)
    }

    @Test
    fun readsAPageInGb18030RatherThanTheEnginesDefault() = runTest {
        // `实验` as the site encodes it: CA B5 D1 E9 in GB18030. Read as UTF-8 these four bytes are
        // replacement characters, and since the parse is what the bookings are made of, that is a page of
        // unnamed experiments.
        val chinese = byteArrayOf(0xCA.toByte(), 0xB5.toByte(), 0xD1.toByte(), 0xE9.toByte())
        val prefix = "<table id=\"Orders_ctl00\"><tr><td>1</td><td><span class=\"linkSmallBold\">"
        val suffix = "</span></td><td>-</td><td><span>Wed 15:55-18:10</span></td>" +
                "<td><span>2026/3/5</span></td><td><span>A101</span></td>" +
                "<td>d</td><td>e</td><td>f</td><td><span></span></td></tr></table>"

        val (api, _) = api {
            respond(
                content = prefix.toByteArray() + chinese + suffix.toByteArray(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=gb2312"),
            )
        }

        val page = api.bookingPage("PhyEws_StuName=waterfloatinggenderly;")

        assertEquals(listOf("实验"), ExperimentParser.rows(page).map { it.subject })
    }

    @Test
    fun switchesCoursesByPostingThePagesOwnFieldsBack() = runTest {
        val page = "<form>" +
                "<input type=\"hidden\" id=\"__VIEWSTATE\" value=\"VIEW\" />" +
                "<input type=\"hidden\" id=\"__EVENTVALIDATION\" value=\"VALID\" />" +
                "<input type=\"submit\" id=\"plan1\$btnSearch\" value=\"查询\" />" +
                "<select id=\"plan1_ExpeList\"><option value=\"0\">one</option></select>" +
                "</form>"
        val (api, requests) = api { respond("", HttpStatusCode.OK) }

        api.selectSubject(
            cookie = "PhyEws_StuName=waterfloatinggenderly;",
            page = page,
            subjectValue = "1",
        )

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("http://wlsy.xidian.edu.cn/PhyEws/student/course.aspx", request.url.toString())
        assertEquals("PhyEws_StuName=waterfloatinggenderly;", request.headers[HttpHeaders.Cookie])
        val body = request.bodyText()
        assertTrue(body.contains("__VIEWSTATE=VIEW"), "The page's hidden fields go back with it.")
        assertTrue(body.contains("__EVENTVALIDATION=VALID"))
        assertTrue(
            body.contains("%24btnSearch="),
            "Every field the page emitted is carried, as the original carried them. Got: $body",
        )
        assertTrue(body.contains("__EVENTTARGET=plan1%24ExpeList"), "Got: $body")
        assertTrue(body.contains("plan1%24ExpeList=1"), "The course being switched to. Got: $body")
    }

    @Test
    fun readsTheBookingAndCoursePagesWithTheCookie() = runTest {
        val (api, requests) = api { respond("<html></html>", HttpStatusCode.OK) }

        api.bookingPage("COOKIE")
        api.coursePage("COOKIE")

        assertEquals(
            listOf(
                "http://wlsy.xidian.edu.cn/PhyEws/student/select.aspx",
                "http://wlsy.xidian.edu.cn/PhyEws/student/course.aspx",
            ),
            requests.map { it.url.toString() },
        )
        assertTrue(requests.all { it.headers[HttpHeaders.Cookie] == "COOKIE" })
    }

    @Test
    fun reportsATransportFailureAsANetworkFailure() = runTest {
        // Standing off campus is the ordinary case, and the cache hint has to be able to name it rather
        // than let an engine-specific exception through.
        val api = ExperimentScheduleApi(
            HttpClient(MockEngine { throw IOException("network is unreachable") }) {
                expectSuccess = false
            },
        )

        assertFailsWith<ExperimentNetworkException> { api.login("20210001", "secret") }
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private fun MockRequestHandleScope.loginAccepted(): HttpResponseData = respond(
        content = "",
        status = HttpStatusCode.Found,
        headers = headersOf(
            HttpHeaders.SetCookie,
            listOf(
                "PhyEws_StuName=%E5%BC%A0%E4%B8%89; path=/",
                "ASP.NET_SessionId=abc; path=/; HttpOnly",
                ".ASPXAUTH=xyz; path=/",
            ),
        ),
    )

    private suspend fun HttpRequestData.bodyText(): String = body.toByteArray().decodeToString()

    /**
     * The site's own encoding for a fixture that has to be Chinese.
     *
     * The charset is spelled out here rather than borrowed from the module, so that a module that failed
     * to resolve GB18030 would fail the page-reading test below rather than agreeing with itself.
     */
    private fun gb(text: String): ByteArray = text.toByteArray(Charsets.forName("GB18030"))

    /** An api whose engine answers with [handler] and records every request. */
    private fun api(
        handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Pair<ExperimentScheduleApi, MutableList<HttpRequestData>> {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        return ExperimentScheduleApi(HttpClient(engine) { expectSuccess = false }) to requests
    }
}
