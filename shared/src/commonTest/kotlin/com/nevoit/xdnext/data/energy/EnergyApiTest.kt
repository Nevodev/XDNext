package com.nevoit.xdnext.data.energy

import com.nevoit.xdnext.core.crypto.AesCbc
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
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Request-shape and response-shape tests for the energy system's HTTP surface.
 *
 * Every expectation about the *wire* here was measured, not guessed: the request bytes come from
 * running the reference's own dio stack in `reference/probe/energy_encoding_probe.dart`, which prints
 * the request line, the headers and the body of each of the five calls. The ciphertexts those calls
 * carry are the ones pinned in [EnergyCryptoTest].
 *
 * The endpoint that matters most is `GetMetRead`, because its response is the whole point of the
 * feature and the one place the server has been seen quoting numbers.
 */
class EnergyApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    // --- request shapes -------------------------------------------------------------------------

    @Test
    fun getSignaturePostsTheLiteralBodyAndNoSignatureHeaders() = runTest {
        // dio implies `application/json` for a Map payload even though the original set no content
        // type, so this call is JSON too — see the probe's first recorded request.
        val (api, requests) = api { signatureBody() }

        val signature = api.getSignature()

        assertEquals("1700000000000", signature.timestamp)
        assertEquals("SIGN", signature.signature)

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(
            "https://ignypt.xidian.edu.cn/baseNew/api/User/GetSignature",
            request.url.toString(),
        )
        assertEquals(
            """{"data":"","access_token":"","OpCode":"MPAY","RequestID":""}""",
            request.bodyText(),
            "All four fields are sent, empty ones included: the server rejects a body missing one.",
        )
        assertNull(request.headers["signature"], "The signature request cannot sign itself.")
    }

    @Test
    fun encodesTheContentParameterTwice() = runTest {
        val (api, requests) = api { request ->
            if (request.url.encodedPath.endsWith("GetSignature")) signatureBody() else meterListBody()
        }

        api.queryMeterList("NODE-1")

        val read = requests.last()
        assertEquals(HttpMethod.Get, read.method)
        // Pass one is `Uri.encodeComponent`, pass two is the HTTP client's own parameter encoding —
        // dio did both, so `+` becomes `%252B` and `/` becomes `%252F` on the wire.
        assertEquals(
            "https://ignypt.xidian.edu.cn/estManage/api/WeChat/V2/H5QueryMeterList" +
                    "?content=dsVLOJGPUrE%252F08FioEpod2dQq%252B7ofB4R8m8G8FoVLl4%253D",
            read.url.toString(),
        )
        assertEquals("1700000000000", read.headers["timestamp"])
        assertEquals("SIGN", read.headers["signature"])
        assertEquals("MPAY", read.headers["OpCode"])
        assertEquals("", read.headers["OrgId"], "Sent empty, exactly as the original sent it.")
        assertEquals("", read.headers["RequestID"])
    }

    @Test
    fun sendsTheLoginBodyThroughAJsonContentEnvelope() = runTest {
        val (api, requests) = api { request ->
            if (request.url.encodedPath.endsWith("GetSignature")) {
                signatureBody()
            } else {
                """{"ResData":[{"NodeID":"NODE-7"}]}"""
            }
        }

        assertEquals("NODE-7", api.h5UserIdLogin("20210001"))

        val login = requests.last()
        assertEquals(HttpMethod.Post, login.method)
        assertEquals(ContentType.Application.Json, login.body.contentType?.withoutParameters())
        // The payload is the encrypted JSON wrapped in `{"content": ...}`, not the JSON itself.
        assertTrue(login.bodyText()!!.startsWith("""{"content":""""), login.bodyText()!!)
    }

    @Test
    fun sendsTheMeterReadWindowAsPlainDates() = runTest {
        val (api, requests) = api { request ->
            if (request.url.encodedPath.endsWith("GetSignature")) {
                signatureBody()
            } else {
                """{"ResData":{"rows":[]}}"""
            }
        }

        api.getMeterRead("MET-9", from = LocalDate(2026, 3, 22), to = LocalDate(2026, 4, 22))

        // The window is encrypted, so it cannot be read back out of the query string; the envelope is
        // pinned by the ciphertext test instead. What matters here is that the call happened at all.
        assertEquals(2, requests.size)
        assertEquals(HttpMethod.Get, requests.last().method)
    }

    // --- response shapes ------------------------------------------------------------------------

    @Test
    fun readsTheMeterListAndItsCodes() = runTest {
        val (api, _) = api { request ->
            if (request.url.encodedPath.endsWith("GetSignature")) {
                signatureBody()
            } else {
                """
                {"ResData":{"rows":[
                  {"MediumCode":"1","MetID":"WATER-1","LastNum":"3.5","LastReadDate":"2026-04-20T00:00:00"},
                  {"MediumCode":"2","MetID":"ELEC-1","LastNum":125.25,"LastReadDate":"2026-04-22"}
                ]}}
                """.trimIndent()
            }
        }

        val rows = api.queryMeterList("NODE-1")

        assertEquals(2, rows.size)
        assertEquals("1", rows[0].mediumCode)
        assertEquals(
            3.5,
            rows[0].lastNum,
            "A quoted number has to survive: the server does quote them."
        )
        assertEquals(
            LocalDate(2026, 4, 20),
            rows[0].lastReadDate,
            "A time part is dropped, not kept."
        )
        assertEquals("2", rows[1].mediumCode)
        assertEquals("ELEC-1", rows[1].metId)
        assertEquals(LocalDate(2026, 4, 22), rows[1].lastReadDate)
    }

    @Test
    fun readsMeterReadingsAndSkipsUnusableRows() = runTest {
        val (api, _) = api { request ->
            if (request.url.encodedPath.endsWith("GetSignature")) {
                signatureBody()
            } else {
                """
                {"ResData":{"rows":[
                  {"ReadTime":"2026-04-20","ReadNum":1.5,"StartNum":100,"EndNum":101.5},
                  {"ReadTime":"2026-04-21T00:00:00","ReadNum":"2","StartNum":"101.5","EndNum":"103.5"},
                  {"ReadTime":"","ReadNum":9,"StartNum":0,"EndNum":0}
                ]}}
                """.trimIndent()
            }
        }

        val readings = api.getMeterRead("MET-9", LocalDate(2026, 3, 22), LocalDate(2026, 4, 22))

        assertEquals(
            2,
            readings.size,
            "The third row has no read time and must be dropped, not thrown."
        )
        assertEquals(MeterInfo(LocalDate(2026, 4, 20), 1.5, 100.0, 101.5), readings[0])
        assertEquals(MeterInfo(LocalDate(2026, 4, 21), 2.0, 101.5, 103.5), readings[1])
    }

    @Test
    fun reportsAResponseWhoseRowsAreAllUnusable() = runTest {
        // A response whose shape changed must fail loudly: an empty reading list would silently become
        // an empty chart, which looks like a working query.
        val (api, _) = api { request ->
            if (request.url.encodedPath.endsWith("GetSignature")) {
                signatureBody()
            } else {
                """{"ResData":{"rows":[{"ReadingTime":"2026-04-20"}]}}"""
            }
        }

        val error = assertFailsWith<EnergyProtocolException> {
            api.getMeterRead("MET-9", LocalDate(2026, 3, 22), LocalDate(2026, 4, 22))
        }
        assertTrue(error.message!!.contains("没有一行"), error.message!!)
    }

    @Test
    fun reportsAMissingResDataInsteadOfReturningNothing() = runTest {
        val (api, _) = api { request ->
            if (request.url.encodedPath.endsWith("GetSignature")) signatureBody() else """{"Code":1}"""
        }

        val error = assertFailsWith<EnergyProtocolException> { api.queryMeterList("NODE-1") }
        assertTrue(error.message!!.contains("ResData"), error.message!!)
    }

    @Test
    fun reportsANonJsonBody() = runTest {
        val (api, _) = api { request ->
            if (request.url.encodedPath.endsWith("GetSignature")) signatureBody() else "<html>登录</html>"
        }

        assertFailsWith<EnergyProtocolException> { api.queryMeterList("NODE-1") }
    }

    @Test
    fun reportsAMissingSignatureAsAProtocolError() = runTest {
        val (api, _) = api { """{"data":{}}""" }

        val error = assertFailsWith<EnergyProtocolException> { api.getSignature() }
        assertTrue(error.message!!.contains("timestamp"), error.message!!)
    }

    @Test
    fun wrapsATransportFailureInANetworkError() = runTest {
        // The cache hint keys off this type, and it is the only way to tell "no campus network" apart
        // from "the server said no" without leaking the HTTP engine into the hint logic.
        val engine = MockEngine { throw RuntimeException("no route to host") }
        val client = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(this@EnergyApiTest.json) }
        }
        val api = EnergyApi(client, EnergyCrypto(AesCbc()), json)

        assertFailsWith<EnergyNetworkException> { api.getSignature() }
    }

    // --- the JSON body of the ciphertext --------------------------------------------------------

    @Test
    fun encryptsTheLoginPayloadWithItsExactFieldNames() = runTest {
        val (api, requests) = api { request ->
            if (request.url.encodedPath.endsWith("GetSignature")) {
                signatureBody()
            } else {
                """{"ResData":[{"NodeID":"NODE-7"}]}"""
            }
        }

        api.h5UserIdLogin("20210001")

        val envelope = json.parseToJsonElement(requests.last().bodyText()!!).jsonObject
        val ciphertext = envelope.getValue("content").jsonPrimitive.content
        assertEquals(
            "bYdVOqDURQRXC6XnFOPnBXb3xfiTSvyNcpPqSLmi4uX5Isvh1VBw+/LKZiFR0XYk7hJiE5Q6M9iPfmt0iYNsmg==",
            ciphertext,
            "`Pwd` must be present and empty, and `IsCehckPwd` must keep the original's spelling: " +
                    "the body that produced this ciphertext is " +
                    """{"UserID":"20210001","Pwd":"","IsCehckPwd":1,"NodeID":""}""",
        )
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun signatureBody(timestamp: String = "1700000000000", signature: String = "SIGN") =
        """{"data":{"timestamp":"$timestamp","signature":"$signature"}}"""

    private fun meterListBody() = """{"ResData":{"rows":[]}}"""

    /** An [EnergyApi] whose engine answers with [handler]'s body and records every request. */
    private fun api(handler: (HttpRequestData) -> String): Pair<EnergyApi, MutableList<HttpRequestData>> {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            respond(
                content = handler(request),
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString()
                ),
            )
        }
        val client = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(this@EnergyApiTest.json) }
        }
        return EnergyApi(client, EnergyCrypto(AesCbc()), json) to requests
    }

    private fun HttpRequestData.bodyText(): String? = (body as? TextContent)?.text
}
