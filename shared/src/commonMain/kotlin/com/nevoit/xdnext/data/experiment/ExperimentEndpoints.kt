package com.nevoit.xdnext.data.experiment

import io.ktor.http.encodeURLParameter

/**
 * The physics experiment system's addresses and the form fields its ASP.NET pages insist on.
 *
 * Ported from `lib/repository/experiment_session/physics_experiment_session.dart` and
 * `experiment_report_session.dart`, and split the way [com.nevoit.xdnext.data.energy] and
 * [com.nevoit.xdnext.data.timetable] are: the addresses live here so the transports read as a
 * sequence of calls rather than a wall of literals.
 *
 * Two hosts are involved, and they are one system:
 *
 *  - `wlsy.xidian.edu.cn/PhyEws/` is the lab booking site the schedule comes from. It is a WebForms
 *    application — every page carries `__VIEWSTATE` and friends, the login is a postback, and the
 *    teacher of a slot only appears after the page has been posted back for the right course;
 *  - `wlsy.xidian.edu.cn/wgyreport/` is the report system the *scores* come from. It is not a
 *    WebForms page but a "UNI GUI" application: its state is built by replaying mouse events as
 *    `HandleEvent` POSTs, which is why reading a score looks the way it does in
 *    [ExperimentReportApi].
 *
 * Unlike the registrars' apps, **neither host is behind IDS**: the lab site has its own login form
 * whose account is the student number and whose password is the separate one the user sets in the
 * settings page. That is why this module never touches [com.nevoit.xdnext.data.session] beyond
 * reading the student number the IDS login already stored.
 */
object ExperimentEndpoints {

    const val ORIGIN = "http://wlsy.xidian.edu.cn"

    /** The WebForms login page. A successful login answers **302**, not 200 — see [loginForm]. */
    const val LOGIN = "$ORIGIN/PhyEws/default.aspx"

    /** The page holding the week/teacher plan grid, one course at a time. */
    const val COURSE = "$ORIGIN/PhyEws/student/course.aspx"

    /** The student's own bookings, in the `Orders_ctl00` table. */
    const val SELECT = "$ORIGIN/PhyEws/student/select.aspx"

    /** The report system's landing page, which mints the session id the score query needs. */
    const val REPORT_HOME = "$ORIGIN/wgyreport/wgyreport.dll/?id=stu"

    /** Every report-system call, including its data query, goes through this address. */
    const val REPORT_EVENT = "$ORIGIN/wgyreport/wgyreport.dll/HandleEvent"

    const val FORM_CONTENT_TYPE = "application/x-www-form-urlencoded"

    /**
     * The login postback's body, with the account and the password substituted in.
     *
     * The four `__VIEWSTATE`-shaped values are the original's own literals, captured once and never
     * recomputed: the page's `plan1`-style hidden fields are stable for this application, and the
     * original sent exactly these strings on every login. A request that asks the login page for a
     * fresh set would work the same way when the server agrees, and would be a *different* request
     * from the one the original has been logging in with since — so the literals stay, and the
     * failure mode of a stale one is a login the server refuses with a message the dialog shows.
     *
     * Two deviations from the original's own expression of this body:
     *
     *  - the account and the password are percent-encoded. The original interpolated them into a raw
     *    body, so a password containing `&`, `=` or a space was sent as fields the server never saw —
     *    the value is a form value either way, and encoding it is what a form value is;
     *  - `login1$btnLogin.x`/`.y` are the coordinates of the login button's click, which is what the
     *    original's browser-shaped request sent. They are not the difference between success and
     *    failure, but they are free to keep.
     */
    fun loginForm(account: String, password: String): String = buildString {
        append("__EVENTTARGET=&__EVENTARGUMENT=&")
        append("__VIEWSTATE=%2FwEPDwUKMTEzNzM0MjM0OWQYAQUeX19D")
        append("b250cm9sc1JlcXVpcmVQb3N0QmFja0tleV9fFgEFD2xvZ2luMSRidG5Mb2dpbkOuzGVaztce4Ict7jsIJ0F5pUDb%2BsmSbCCrNVSBlPML&")
        append("__VIEWSTATEGENERATOR=EE008CD9&")
        append("__EVENTVALIDATION=%2FwEdAAcKecdPGDB%2BfW8Tyghx")
        append("7AeSpOzeiNZ7aaEg5p6LqSa9cODI2bZwNtRxUKPkisVLf8l")
        append("8Vv4WhRVIIhZlyYNJO%2BySrDKOhP%2B%2FYMNbVIh74hA2r")
        append("CYnBBSTsX9SjxiYNNk%2B5kglM%2B6pGIq22Oi5mNu6u6eC2W")
        append("EBfKAmATKwSpsOL%2FPNcRyi9l8Dnp6JamksyAzjhW4%3D&")
        append("login1%24StuLoginID=").append(account.encodeURLParameter(spaceToPlus = true))
        append("&login1%24StuPassword=").append(password.encodeURLParameter(spaceToPlus = true))
        append("&login1%24UserRole=Student")
        append("&login1%24btnLogin.x=28&login1%24btnLogin.y=14")
    }

    /**
     * The two lab slots a physics experiment can be booked in, as the original derived them.
     *
     * The site's time column is only sniffed for the substring `15` to choose between them
     * (`timeStr.contains("15")`), which is why the choice lives here rather than in the parser: it is
     * not a parse of the time, it is a test of which of the two bell times the row's text mentions.
     * The two pairs are the lab's actual sessions — an afternoon one and an evening one, each three
     * class hours long, which is what the `（3学时）` suffix on a booking's name records.
     */
    const val AFTERNOON_MARKER = "15"
    const val AFTERNOON_START_HOUR = 15
    const val AFTERNOON_START_MINUTE = 55
    const val AFTERNOON_STOP_HOUR = 18
    const val AFTERNOON_STOP_MINUTE = 10
    const val EVENING_START_HOUR = 18
    const val EVENING_START_MINUTE = 30
    const val EVENING_STOP_HOUR = 20
    const val EVENING_STOP_MINUTE = 45

    /**
     * The report system's own headers, which its session manager checks before it will run.
     *
     * The `Origin`/`Referer` pair is the reason these are written down: the same application refuses a
     * request that does not claim it came from its own page, and the referer names the student entry
     * point rather than the page being read.
     *
     * `Accept-Encoding` is deliberately **not** reproduced. The original asked for
     * `gzip, deflate, br`; this port's engine negotiates its own and decompresses what it negotiated,
     * while a hand-written list would be returned to the caller compressed — a header that buys
     * nothing and can only corrupt the body.
     */
    val ReportHeaders: Map<String, String> = mapOf(
        "Accept-Language" to "zh-CN,zh;q=0.9",
        "User-Agent" to
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36",
        "Connection" to "keep-alive",
        "Origin" to ORIGIN,
        "Referer" to REPORT_HOME,
    )

    /** The `Accept` a report-system page read sends. */
    const val REPORT_PAGE_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"

    /** The `Accept` a report-system event sends, which is an XHR rather than a page read. */
    const val REPORT_XHR_ACCEPT = "*/*"

    /** The event POSTs' own content type — the page reads send no body at all. */
    const val REPORT_POST_CONTENT_TYPE = "application/x-www-form-urlencoded; charset=UTF-8"
}
