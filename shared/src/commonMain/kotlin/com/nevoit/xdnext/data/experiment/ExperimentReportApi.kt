package com.nevoit.xdnext.data.experiment

import com.nevoit.xdnext.core.log.appLog
import com.nevoit.xdnext.core.platform.currentTimeMillis
import com.fleeksoft.charset.Charsets
import com.fleeksoft.charset.decodeToString
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The report system's HTTP surface: where each experiment's score image lives, and the bytes of one.
 *
 * The lab site never says what a mark *is* — it shows a picture, and the picture is the mark. So the
 * module's two halves are separate systems on the same host: [ExperimentScheduleApi] reads the
 * bookings, and this reads the pictures they were marked with.
 *
 * This is not a WebForms page. `wgyreport.dll` is a "UNI GUI" application whose state is assembled by
 * *replaying input events*: the client has to send a window-size report, a mouse move, an activate, a
 * resize and finally a click carrying the credentials, each numbered with `_seq_`, before the same
 * endpoint will answer a data query. Every one of those bodies is the original's own literal, and they
 * are sent in its order — a session that skips a step gets a state the query is not in.
 *
 * Read only from the campus network, like everything else behind this host.
 */
internal class ExperimentReportApi(
    private val client: HttpClient,
    private val json: Json,
    private val clockMs: () -> Long = ::currentTimeMillis,
) {

    /**
     * Each experiment's score image, keyed by the experiment's name.
     *
     * An empty answer is a *result*, not a failure: an account with nothing marked yet has no images,
     * and the original's own service threw here — which its caller caught and logged anyway.
     */
    suspend fun scoreImageUrls(account: String, password: String): Map<String, String> {
        val home = execute("打开成绩报告页") {
            client.get(ExperimentEndpoints.REPORT_HOME) {
                reportHeaders()
                header(HttpHeaders.Accept, ExperimentEndpoints.REPORT_PAGE_ACCEPT)
            }
        }

        // The session id the events are numbered against, and it comes from a *response header* of the
        // landing page rather than from its markup — the original read it the same way and carried the
        // page's cookies alongside it.
        val sessionId = home.headers[SESSION_HEADER].orEmpty()
        if (sessionId.isEmpty()) {
            throw ExperimentProtocolException("报告系统未返回会话标识")
        }
        var cookie = home.headers.getAll(HttpHeaders.SetCookie).orEmpty().joinToString("; ") {
            it.substringBefore(';')
        }

        // The five events, in the original's own order and with its own coordinates.
        event(cookie, "Ajax=1&IsEvent=1&Obj=O0&Evt=cinfo&ci=br%3D33%3Bos%3D4%3Bbv%3D140%3Bww%3D2048%3Bwh%3D1018&_S_ID=$sessionId&_seq_=0&_uo_=O0")
        event(cookie, "Ajax=1&IsEvent=1&Obj=O0&Evt=move&this=O0&x=868&y=381&_S_ID=$sessionId&_seq_=1&_uo_=O0")
        event(cookie, "Ajax=1&IsEvent=1&Obj=O0&Evt=activate&this=O0&_S_ID=$sessionId&_seq_=2&_uo_=O0")
        event(cookie, "Ajax=1&IsEvent=1&Obj=O0&Evt=resize&this=O0&w=311&h=255&_S_ID=$sessionId&_seq_=3&_uo_=O0")

        // The click is the login: the credentials travel inside its `_fp_` field, as a nested
        // form-encoded structure. Both values are interpolated **raw**, exactly as the original
        // interpolated them — encoding them here would add a level to a payload that is already
        // percent-encoded twice, which is a worse guess than the original's own bytes. A password
        // containing `&` would therefore break this one request, in this port as in the reference.
        val click = execute("登录成绩报告系统") {
            client.post(ExperimentEndpoints.REPORT_EVENT) {
                reportHeaders()
                header(HttpHeaders.Accept, ExperimentEndpoints.REPORT_XHR_ACCEPT)
                header(X_REQUESTED_WITH, "XMLHttpRequest")
                contentType(ContentType.parse(ExperimentEndpoints.REPORT_POST_CONTENT_TYPE))
                header(HttpHeaders.Cookie, cookie)
                setBody(
                    "Ajax=1&IsEvent=1&Obj=O1F&Evt=click&this=O1F&_S_ID=$sessionId" +
                            "&_fp_=%26O17%3D%25020%2502%2502$account" +
                            "%26O1B%3D%25020%2502%2502$password&_seq_=4&_uo_=O0",
                )
            }
        }

        // The click answers with the session cookie the data query is read under. Only the `sid` pair
        // is of interest, and it is rebuilt into the header the original rebuilt it into.
        click.headers.getAll(HttpHeaders.SetCookie).orEmpty()
            .firstOrNull { it.startsWith("$SID_COOKIE=") }
            ?.let { line ->
                val value = line.substringBefore(';').substringAfter('=')
                cookie = "UNI_GUI_SESSION_ID=$sessionId; $SID_COOKIE=$value"
            }

        val data = execute("查询成绩列表") {
            client.get(ExperimentEndpoints.REPORT_EVENT) {
                reportHeaders()
                header(HttpHeaders.Accept, ExperimentEndpoints.REPORT_XHR_ACCEPT)
                header(X_REQUESTED_WITH, "XMLHttpRequest")
                header(UNI_SESSION_HEADER, sessionId)
                header(SESSION_HEADER, sessionId)
                header(HttpHeaders.Cookie, cookie)
                parameter("IsEvent", "1")
                parameter("Obj", "OA7")
                parameter("Evt", "data")
                // A cache buster, which is what the original's `_dc` is.
                parameter("_dc", clockMs().toString())
                parameter("start", "0")
                parameter("limit", "25")
                parameter("options", "1")
                parameter("page", "1")
            }
        }

        return parseScoreImageUrls(
            json = json,
            body = decodeReportText(data.readRawBytes(), data.headers[HttpHeaders.ContentType]),
        )
    }

    /** One image's bytes, for the page that shows what a mark's picture looks like. */
    suspend fun imageBytes(url: String): ByteArray {
        val response = execute("下载成绩图片") { client.get(url) }
        return response.readRawBytes()
    }

    /** One event POST, whose answer is discarded: what matters is the state it leaves. */
    private suspend fun event(cookie: String, body: String) {
        execute("成绩报告会话") {
            client.post(ExperimentEndpoints.REPORT_EVENT) {
                reportHeaders()
                header(HttpHeaders.Accept, ExperimentEndpoints.REPORT_XHR_ACCEPT)
                header(X_REQUESTED_WITH, "XMLHttpRequest")
                contentType(ContentType.parse(ExperimentEndpoints.REPORT_POST_CONTENT_TYPE))
                if (cookie.isNotEmpty()) header(HttpHeaders.Cookie, cookie)
                setBody(body)
            }
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.reportHeaders() {
        ExperimentEndpoints.ReportHeaders.forEach { (name, value) -> header(name, value) }
    }

    private suspend fun execute(label: String, block: suspend () -> HttpResponse): HttpResponse = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        throw ExperimentNetworkException("$label 请求失败", error)
    }

    private companion object {
        /** The session id, which the landing page answers in a header of its own name. */
        const val SESSION_HEADER = "session_id"

        /** The header the data query names the same session by, capitalised as the original sent it. */
        const val UNI_SESSION_HEADER = "UniSessionId"

        const val SID_COOKIE = "sid"
        const val X_REQUESTED_WITH = "X-Requested-With"
    }
}

/**
 * The experiment name to image URL map out of the report system's data answer.
 *
 * The answer is a JSON list of grid rows, whose cells are keyed by *column number as a string* — the
 * experiment's name is row `"2"` and row `"3"` is an HTML fragment carrying the image. The fragment's
 * markup arrives escaped (`\x3C` for `<`), which is why the two replacements come first; the original
 * did the same. A row without a source or without a name is skipped rather than guessed at.
 */
internal fun parseScoreImageUrls(json: Json, body: String): Map<String, String> {
    val unescaped = body.replace("""\x3C""", "<").replace("""\x3E""", ">")
    val root = runCatching { json.parseToJsonElement(unescaped) as? JsonObject }.getOrNull()
    val rows = (root?.get("rows") as? JsonArray).orEmpty()

    val urls = linkedMapOf<String, String>()
    rows.forEach { element ->
        val row = element as? JsonObject ?: return@forEach
        val name = (row["2"] as? JsonPrimitive)?.contentOrEmpty().orEmpty()
        val imageHtml = (row["3"] as? JsonPrimitive)?.contentOrEmpty().orEmpty()
        if (name.isEmpty()) return@forEach

        val source = ImageSource.find(imageHtml)?.groupValues?.get(1).orEmpty()
        if (source.isEmpty()) return@forEach

        // A relative path is served from the same host as the report system.
        urls[name] = if (source.startsWith("/")) ExperimentEndpoints.ORIGIN + source else source
    }
    return urls
}

/**
 * The image address inside a `src="…"` attribute, escaped or not.
 *
 * The original's own pattern (`src=\"?([^\"]+)\"?`), which matches both an attribute in the fragment
 * and one that has been escaped once more.
 */
private val ImageSource = Regex("""src=\\?"([^"]+)\\?""")

/** A JSON primitive's content, or empty when the cell holds something that is not a primitive. */
private fun JsonPrimitive.contentOrEmpty(): String = content

/**
 * The report system's answer as text, by what it is.
 *
 * Three cases, and they are the original's own: a body that starts with `{` or `[` is JSON and is
 * UTF-8; a body whose content type says `gb` is a page and is the site's own charset; anything else is
 * read byte for byte, which is what `String.fromCharCodes` did and what makes an unexpected answer
 * visible in the log rather than silently empty.
 */
internal fun decodeReportText(bytes: ByteArray, contentType: String?): String = when {
    bytes.isEmpty() -> ""
    bytes[0] == '{'.code.toByte() || bytes[0] == '['.code.toByte() ->
        bytes.decodeToString(Charsets.UTF8)
    contentType?.contains("gb", ignoreCase = true) == true -> bytes.decodeSiteText()
    else -> {
        appLog.w { "[ExperimentReportApi] ${bytes.size} bytes of unknown text; reading them raw" }
        bytes.decodeToString(Charsets.ISO_8859_1)
    }
}
