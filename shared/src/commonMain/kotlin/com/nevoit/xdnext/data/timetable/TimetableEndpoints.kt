package com.nevoit.xdnext.data.timetable

/**
 * The timetable's endpoints and the headers its host insists on.
 *
 * Ported from the constants in `lib/repository/ids_session/classtable_session.dart` and the eHall
 * branch of `lib/repository/network_client.dart`. Split the way [com.nevoit.xdnext.data.energy] is:
 * the addresses live here so the transport in [TimetableApi] reads as a sequence of calls rather than
 * a wall of literals, and so the postgraduate branch — a different host with a different set of
 * endpoints — has somewhere to go that is not inside the undergraduate one.
 */
object TimetableEndpoints {

    const val EHALL_ORIGIN = "https://ehall.xidian.edu.cn"

    /**
     * The eHall app the timetable lives in.
     *
     * `4770397878132218` is the app id the original used for every academic module, and it is what
     * IDS has to be asked for a ticket *for*: the ticket authorises one app, and the timetable's
     * endpoints are refused under a ticket minted for another.
     */
    const val APP_ID = "4770397878132218"

    /** The CAS service to log in for, matching the original's `checkAndLogin` target. */
    const val SERVICE = "$EHALL_ORIGIN/appShow?appId=$APP_ID"

    private const val Jwapp = "$EHALL_ORIGIN/jwapp/sys/wdkb/modules"

    /** `dqxnxq` — the semester the registrar currently considers current. */
    const val CURRENT_SEMESTER = "$Jwapp/jshkcb/dqxnxq.do"

    /** `cxjcs` — the day the given semester starts teaching. */
    const val TERM_START_DAY = "$Jwapp/jshkcb/cxjcs.do"

    /** `xskcb` — the student's own timetable rows. */
    const val CLASS_TABLE = "$Jwapp/xskcb/xskcb.do"

    /** `cxxsllsywpk` — courses with no time arrangement. */
    const val NOT_ARRANGED = "$Jwapp/xskcb/cxxsllsywpk.do"

    /** `xsdkkc` — the registrar's schedule adjustments (调课/停课/补课). */
    const val CLASS_CHANGE = "$Jwapp/xskcb/xsdkkc.do"

    /**
     * The headers eHall's WAF expects, reproduced from the original's `_ehallHeaders`.
     *
     * All six are sent on every request to this host, including the `Referer`: eHall answers some of
     * these endpoints with its login page rather than the data when the request does not look like one
     * the web app would make. The original attached them through an interceptor keyed on the host name
     * and sent them on the `POST`s as well, so that is where they are attached here.
     */
    val EhallHeaders: Map<String, String> = mapOf(
        "Referer" to "http://ehall.xidian.edu.cn/new/index_xd.html",
        "Accept" to
                "text/html,application/xhtml+xml,application/xml;q=0.9," +
                "image/webp,image/apng,*/*;q=0.8," +
                "application/signed-exchange;v=b3;q=0.9",
        "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6",
        "Accept-Encoding" to "identity",
        "Connection" to "Keep-Alive",
    )

    /** The one header above that is set from a typed property rather than the map. */
    const val EHALL_FORM_CONTENT_TYPE = "application/x-www-form-urlencoded; charset=UTF-8"

    /** The `*order` value `xsdkkc` is asked for: newest adjustment first. */
    const val CLASS_CHANGE_ORDER = "-SQSJ"
}
