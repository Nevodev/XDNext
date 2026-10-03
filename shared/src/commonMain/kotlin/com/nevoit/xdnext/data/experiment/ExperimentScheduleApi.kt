package com.nevoit.xdnext.data.experiment

import com.fleeksoft.ksoup.Ksoup
import com.nevoit.xdnext.core.log.appLog
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

/**
 * The lab site's HTTP surface: log in, read the bookings, read one course's plan grid.
 *
 * Split from [ExperimentSession] the way [com.nevoit.xdnext.data.timetable.TimetableApi] is split from
 * its session: this class performs the requests and hands back **HTML**, while the session owns the
 * credentials, the sequence and the cache fallback, and [ExperimentParser] turns the HTML into models.
 *
 * The site is a WebForms application, which shows up in three places: a login that answers `302` when
 * it works and the page again when it does not, a cookie the pages are read with, and a course list
 * that can only be switched by posting the page's own hidden fields back.
 */
internal class ExperimentScheduleApi(private val client: HttpClient) {

    /**
     * Logs in and returns the `Cookie` header the rest of the session is read with.
     *
     * A refused login is not a transport failure: the site answers `200` with the login page and puts
     * its own sentence in `login1_Label1`, which is what the exception carries.
     */
    suspend fun login(account: String, password: String): String {
        val response = execute("登录物理实验系统") {
            client.post(ExperimentEndpoints.LOGIN) {
                contentType(ContentType.parse(ExperimentEndpoints.FORM_CONTENT_TYPE))
                // The body is sent exactly as the original's own string — see `loginForm`: the
                // view-state values are already percent-encoded, so a form encoder would encode them a
                // second time and the server would read different hidden fields than the page emitted.
                setBody(ExperimentEndpoints.loginForm(account, password))
            }
        }

        if (response.status.value != LOGIN_REDIRECT) {
            val html = response.readRawBytes().decodeSiteText()
            appLog.w { "[ExperimentScheduleApi] Login answered HTTP ${response.status.value}" }
            throw ExperimentLoginFailedException(ExperimentParser.loginError(html))
        }
        return cookieOf(response)
    }

    /** The student's own bookings, from `select.aspx`. */
    suspend fun bookingPage(cookie: String): String = page(ExperimentEndpoints.SELECT, cookie)

    /** The plan grid, which shows the slots and teachers of one course. */
    suspend fun coursePage(cookie: String): String = page(ExperimentEndpoints.COURSE, cookie)

    /**
     * Switches the plan grid to the course whose form value is [subjectValue], and returns the grid.
     *
     * The postback carries every hidden field the page emitted — the view state included — plus the
     * form's own `plan1$ExpeList` value, which is what the select would have submitted had a browser
     * changed it. A field with no id is skipped rather than sent under an empty name: WebForms gives
     * every field it reads an id, so a nameless one is markup rather than state.
     */
    suspend fun selectSubject(cookie: String, page: String, subjectValue: String): String {
        val form = postbackForm(page, subjectValue)
        val response = execute("读取物理实验教师") {
            client.submitForm(
                url = ExperimentEndpoints.COURSE,
                formParameters = Parameters.build { form.forEach { (key, value) -> append(key, value) } },
            ) {
                header(HttpHeaders.Cookie, cookie)
            }
        }
        return response.readRawBytes().decodeSiteText()
    }

    /** The hidden fields a postback has to carry, plus the event and the value being selected. */
    private fun postbackForm(page: String, subjectValue: String): Map<String, String> {
        val fields = linkedMapOf<String, String>()
        Ksoup.parse(page).getElementsByTag("input").forEach { input ->
            val id = input.attr("id")
            if (id.isNotEmpty()) fields[id] = input.attr("value")
        }
        // After the page's own fields, so these win if the page happens to carry them too.
        fields[EVENT_TARGET] = SUBJECT_LIST_EVENT
        fields[SUBJECT_LIST_EVENT] = subjectValue
        appLog.i { "[ExperimentScheduleApi] Posting back ${fields.size} fields for one course" }
        return fields
    }

    private suspend fun page(url: String, cookie: String): String {
        val response = execute("读取物理实验页面") {
            client.get(url) { header(HttpHeaders.Cookie, cookie) }
        }
        return response.readRawBytes().decodeSiteText()
    }

    /**
     * The cookies the login answered with, as one header.
     *
     * Three things here are the original's, and the second is the odd one:
     *
     *  - an `HttpOnly` cookie is dropped. It is not that the engine hides it — the original's HTTP
     *    layer never kept a jar at all — but that its own filter skipped those lines, so a request
     *    built here matches the requests it made;
     *  - **the student-name cookie is replaced with a fixed value.** The original did exactly this
     *    ("This guy find out the secret"), and it is kept because the site evidently only checks that
     *    the cookie is *present*: the real value is the student's name in Chinese, and sending it
     *    un-encoded — which is what a hand-built header does — is likelier to be refused than this;
     *  - only the first `name=value` pair of each `Set-Cookie` line is kept, the attributes being the
     *    client's business rather than the server's.
     */
    private fun cookieOf(response: HttpResponse): String = buildString {
        response.headers.getAll(HttpHeaders.SetCookie).orEmpty().forEach { line ->
            when {
                line.contains(STUDENT_NAME_COOKIE) -> append("$STUDENT_NAME_COOKIE=$STUDENT_NAME_PLACEHOLDER; ")
                line.contains("HttpOnly") -> Unit
                else -> append(line.substringBefore(';')).append("; ")
            }
        }
    }

    /** Runs a request, translating anything the engine threw into [ExperimentNetworkException]. */
    private suspend fun execute(label: String, block: suspend () -> HttpResponse): HttpResponse = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        throw ExperimentNetworkException("$label 请求失败", error)
    }

    private companion object {
        /** WebForms answers a successful login with a redirect, and that status is the verdict. */
        const val LOGIN_REDIRECT = 302

        const val EVENT_TARGET = "__EVENTTARGET"
        const val SUBJECT_LIST_EVENT = "plan1\$ExpeList"

        const val STUDENT_NAME_COOKIE = "PhyEws_StuName"

        /**
         * The value the original sent in place of the real student-name cookie.
         *
         * Kept verbatim rather than paraphrased: whatever the site checks, it accepted this string, and
         * a different one is a different request.
         */
        const val STUDENT_NAME_PLACEHOLDER = "waterfloatinggenderly"
    }
}
