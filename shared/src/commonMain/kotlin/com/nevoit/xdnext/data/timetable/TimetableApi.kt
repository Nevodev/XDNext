package com.nevoit.xdnext.data.timetable

import com.nevoit.xdnext.core.log.appLog
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** The registrar answered in a way this port does not define. */
class TimetableProtocolException(message: String) : Exception(message)

/**
 * eHall could not be reached, or the exchange was cut short.
 *
 * The same split the energy and campus-card modules make: transport failures become this type here so
 * nothing above has to know the HTTP engine's exception hierarchy, which differs per platform.
 */
class TimetableNetworkException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * The registrar's verdict on the student's timetable for a semester.
 *
 * "Not published yet" is a **normal answer**, not a failure: the registrar serves an empty timetable
 * for a semester it has not opened and says so in `extParams`. The original answered a
 * [ClassTableData] with a semester code and a term start day but no rows, and the page then showed its
 * empty state — so the distinction has to survive this far up.
 */
internal sealed interface ClassTableAnswer {

    /** The rows the registrar returned. May legitimately be empty. */
    data class Rows(val value: List<JsonObject>) : ClassTableAnswer

    /** The registrar has not published this semester. */
    data object NotPublished : ClassTableAnswer
}

/**
 * The HTTP surface of the registrar's timetable.
 *
 * Split from [TimetableSession] the way [com.nevoit.xdnext.data.energy.EnergyApi] is split from its
 * session: this class performs the requests and hands back *wire rows*, while the session owns the
 * login, the semester choice and the cache fallback, and [TimetableParser] turns rows into models.
 *
 * The client is the **IDS client** — the same cookie jar the CAS hop just filled, because that hop is
 * what authorises these calls at all. eHall also insists on a browser-shaped request: the six headers
 * of [TimetableEndpoints.EhallHeaders] go on every call, and the body is form-encoded rather than
 * JSON, which is what its own web app sends.
 */
internal class TimetableApi(
    private val client: HttpClient,
    private val json: Json,
) {

    /**
     * The semester the registrar currently considers current, from `dqxnxq`.
     *
     * The original kept this in a `SemesterController` of its own. It is read here because the
     * timetable is the only consumer that needs it *during* a refresh, and because the semester has to
     * be settled before the timetable can be asked for: [TimetableSession] is where the remote code
     * and the locally remembered one are reconciled.
     */
    suspend fun currentSemesterCode(): String {
        val body = post("dqxnxq", TimetableEndpoints.CURRENT_SEMESTER)
        return body.firstRow("dqxnxq")?.text("DM")?.takeIf { it.isNotBlank() }
            ?: throw TimetableProtocolException("dqxnxq 未返回当前学期代码")
    }

    /** The day the given semester starts teaching, from `cxjcs`. */
    suspend fun termStartDay(semesterCode: String): String {
        val yearAndTerm = SemesterCode.academicYearAndTerm(semesterCode)
            ?: throw TimetableProtocolException("学期代码格式无法解析：$semesterCode")

        val body = post(
            label = "cxjcs",
            url = TimetableEndpoints.TERM_START_DAY,
            form = mapOf("XN" to yearAndTerm.first, "XQ" to yearAndTerm.second),
        )
        return body.firstRow("cxjcs")?.text("XQKSRQ")?.takeIf { it.isNotBlank() }
            ?: throw TimetableProtocolException("cxjcs 未返回学期开始日期")
    }

    /** The student's timetable rows for [semesterCode], or the registrar's "not published" verdict. */
    suspend fun classTableRows(semesterCode: String, account: String): ClassTableAnswer {
        val data = post(
            label = "xskcb",
            url = TimetableEndpoints.CLASS_TABLE,
            form = mapOf("XNXQDM" to semesterCode, "XH" to account),
        ).datas("xskcb") ?: throw TimetableProtocolException("xskcb 响应缺少 datas.xskcb")

        // eHall marks a successful answer with `extParams.code == 1`, and reports everything else in
        // `extParams.msg`. One of those messages is "the semester has not been published", which is a
        // result the page has an empty state for; every other message is a failure worth throwing.
        val verdict = data.obj("extParams")
        val code = verdict?.int("code")
        if (code != null && code != 1) {
            val message = verdict.text("msg").orEmpty()
            appLog.w { "[TimetableApi] xskcb extParams: $message" }
            if (message.contains(NOT_PUBLISHED_MARKER)) return ClassTableAnswer.NotPublished
            throw TimetableProtocolException("xskcb 返回错误：$message")
        }
        return ClassTableAnswer.Rows(data.rows())
    }

    /** The courses that exist but have no place in the grid, from `cxxsllsywpk`. */
    suspend fun notArrangedRows(semesterCode: String, account: String): List<JsonObject> =
        post(
            label = "cxxsllsywpk",
            url = TimetableEndpoints.NOT_ARRANGED,
            form = mapOf("XNXQDM" to semesterCode, "XH" to account),
        ).datas("cxxsllsywpk")?.rows().orEmpty()

    /**
     * The registrar's schedule adjustments, from `xsdkkc`.
     *
     * The endpoint reports its total separately from the rows it returns, and the original only walked
     * the rows when that total was positive — an empty list with a positive total would have been
     * walked harmlessly, and a positive list with a zero total would have been skipped. Reading the
     * rows unconditionally is the same answer for both, and one fewer way to lose a change.
     *
     * A failure here is deliberately *not* fatal to the refresh: [TimetableSession] catches it and
     * carries on with no adjustments, because the original logged this one and continued. A timetable
     * without its 调课 annotations is worth far more than no timetable.
     */
    suspend fun classChangeRows(semesterCode: String): List<JsonObject> =
        post(
            label = "xsdkkc",
            url = TimetableEndpoints.CLASS_CHANGE,
            form = mapOf(
                "XNXQDM" to semesterCode,
                "*order" to TimetableEndpoints.CLASS_CHANGE_ORDER,
            ),
        ).datas("xsdkkc")?.rows().orEmpty()

    /** One form-encoded `POST`, parsed as a JSON object. */
    private suspend fun post(
        label: String,
        url: String,
        form: Map<String, String> = emptyMap(),
    ): JsonObject {
        appLog.i { "[TimetableApi] POST $label, form=${form.keys}" }
        val response = execute {
            client.submitForm(
                url = url,
                formParameters = Parameters.build { form.forEach { (k, v) -> append(k, v) } },
            ) {
                ehallHeaders()
            }
        }
        val text = response.bodyAsText()
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
        if (root == null) {
            // eHall answers a request it will not serve with its login page at HTTP 200, so the status
            // code is not the verdict and the body has to be named in the failure.
            appLog.w {
                "[TimetableApi] $label answered HTTP ${response.status.value}, " +
                        "${text.length} chars, body=${text.replace(Regex("\\s+"), " ").take(200)}"
            }
            throw TimetableProtocolException(
                "$label 返回了非 JSON 响应 (HTTP ${response.status.value})",
            )
        }
        return root
    }

    /**
     * The headers eHall's WAF expects.
     *
     * `Content-Type` is set by `submitForm` itself — it is the whole point of that call — so only the
     * five that are not the body's type are attached here.
     */
    private fun HttpRequestBuilder.ehallHeaders() {
        TimetableEndpoints.EhallHeaders.forEach { (name, value) -> header(name, value) }
    }

    /** Runs a request, translating anything the engine threw into [TimetableNetworkException]. */
    private suspend fun execute(block: suspend () -> HttpResponse): HttpResponse = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        throw TimetableNetworkException("课表请求失败", error)
    }

    private companion object {
        /**
         * The registrar's own wording for "this semester has not been published".
         *
         * Matched by `contains` rather than equality, and read from the raw message, exactly as the
         * original did: the server wraps it in punctuation that has changed before.
         */
        const val NOT_PUBLISHED_MARKER = "查询学年学期的课程未发布"
    }
}
