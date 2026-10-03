package com.nevoit.xdnext.data.experiment

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for the report system's HTTP surface.
 *
 * This is the oddest protocol in the app: the endpoint is a "UNI GUI" application whose state is built by
 * *replaying input events* — a window-size report, a mouse move, an activate, a resize, and a click that
 * carries the credentials — before the same address will answer a data query. The bodies are the
 * original's own literals, so what is pinned here is that all five are sent, in that order, and that the
 * click really carries the credentials; the rest is the data query's own shape and the JSON it answers.
 */
class ExperimentReportApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Test
    fun walksTheFiveEventsBeforeAskingForTheData() = runTest {
        val (api, requests) = api()

        api.scoreImageUrls("20210001", "secret")

        assertEquals(7, requests.size, "The landing page, five events, then the query.")
        assertEquals(
            listOf("cinfo", "move", "activate", "resize", "click"),
            requests.drop(1).take(5).map { it.event() },
        )
        assertTrue(requests.first().url.toString().contains("id=stu"), "The session's landing page.")
        assertTrue(
            requests.drop(1).take(5).all { it.url.toString() == ExperimentEndpoints.REPORT_EVENT },
            "Every event goes to the same endpoint.",
        )
    }

    @Test
    fun theClickCarriesTheCredentialsInTheNestedPayloadTheOriginalSent() = runTest {
        val (api, requests) = api()

        api.scoreImageUrls("20210001", "secret")

        val click = requests.single { it.event() == "click" }
        val body = (click.body as? TextContent)?.text.orEmpty()
        assertTrue(
            body.contains("_fp_=%26O17%3D%25020%2502%250220210001%26O1B%3D%25020%2502%2502secret"),
            "Both credentials travel inside the click's own payload. Got: $body",
        )
        assertTrue(body.contains("_S_ID=SID123"), "The session the landing page answered with.")
        assertTrue(body.contains("_seq_=4"))
    }

    @Test
    fun theDataQueryNamesTheSessionAndTheRefreshedCookie() = runTest {
        val (api, requests) = api()

        api.scoreImageUrls("20210001", "secret")

        val query = requests.last()
        assertEquals(HttpMethod.Get, query.method)
        assertEquals("OA7", query.url.parameters["Obj"])
        assertEquals("data", query.url.parameters["Evt"])
        assertEquals("SID123", query.headers["UniSessionId"])
        assertEquals(
            "UNI_GUI_SESSION_ID=SID123; sid=SID999",
            query.headers[HttpHeaders.Cookie],
            "The click's `sid` is what the query is read under, as the original rebuilt it.",
        )
    }

    @Test
    fun readsTheImageUrlsOutOfTheGridRows() = runTest {
        val (api, _) = api()

        val urls = api.scoreImageUrls("20210001", "secret")

        assertEquals(
            mapOf(
                "示波器的使用" to "http://wlsy.xidian.edu.cn/score/1.png",
                "霍尔效应" to "http://other.example/2.png",
            ),
            urls,
            "A relative path is served from the report system's own host.",
        )
    }

    @Test
    fun aSessionWithNoSessionIdIsAProtocolFailure() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.OK) }
        val api = ExperimentReportApi(HttpClient(engine) { expectSuccess = false }, json)

        assertFailsWith<ExperimentProtocolException> { api.scoreImageUrls("20210001", "secret") }
    }

    @Test
    fun readsAnAnswerByWhatItIs() {
        assertEquals("""{"a":1}""", decodeReportText("""{"a":1}""".encodeToByteArray(), null))
        assertEquals(
            "实验",
            decodeReportText(
                byteArrayOf(0xCA.toByte(), 0xB5.toByte(), 0xD1.toByte(), 0xE9.toByte()),
                "text/html; charset=gb2312",
            ),
        )
        assertEquals(
            "plain",
            decodeReportText("plain".encodeToByteArray(), "application/json"),
            "Neither JSON nor a `gb` page: the bytes are read as they are rather than guessed at.",
        )
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private fun HttpRequestData.event(): String? =
        (body as? TextContent)?.text?.let { Regex("""Evt=([a-z]+)""").find(it)?.groupValues?.get(1) }

    /** An api whose engine plays the report system's own conversation. */
    private fun api(): Pair<ExperimentReportApi, MutableList<HttpRequestData>> {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            when {
                request.url.encodedPath.endsWith("HandleEvent") && request.event() == "click" -> respond(
                    content = "",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.SetCookie, "sid=SID999; path=/"),
                )

                request.url.encodedPath.endsWith("HandleEvent") &&
                        request.url.parameters["Evt"] == "data" -> respond(
                    content = DATA,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json; charset=UTF-8"),
                )

                request.url.encodedPath.endsWith("HandleEvent") -> respond("", HttpStatusCode.OK)

                else -> respond(
                    content = "<html></html>",
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        "session_id" to listOf("SID123"),
                        HttpHeaders.SetCookie to listOf("UNI_GUI_SESSION_ID=SID123; path=/"),
                    ),
                )
            }
        }
        return ExperimentReportApi(
            client = HttpClient(engine) { expectSuccess = false },
            json = json,
        ) to requests
    }

    private companion object {
        /** The data answer, escaped the way the original's own service sends it. */
        val DATA = """
            {"rows":[
              {"2":"示波器的使用","3":"<img src=\"/score/1.png\">"},
              {"2":"霍尔效应","3":"<img src=\"http://other.example/2.png\">"},
              {"2":"没有图片的实验","3":"<td>无</td>"},
              {"3":"<img src=\"/nameless.png\">"}
            ]}
        """.trimIndent()
    }
}
