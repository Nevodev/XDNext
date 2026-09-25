package com.nevoit.xdnext.data.schoolcard

import com.nevoit.xdnext.data.schoolcard.SchoolCardEndpoints.OAUTH_ENTRY
import io.ktor.http.encodeURLParameter

/**
 * Endpoints of the campus card (一卡通) system, and the IDS service that lets this app in.
 *
 * Ported from `lib/repository/ids_session/school_card_session.dart`. The card host is
 * `v8scan.xidian.edu.cn` — the "scan code to pay" site behind the card's QR code — which is where both
 * the balance and the transaction list live.
 */
object SchoolCardEndpoints {

    const val ORIGIN = "https://v8scan.xidian.edu.cn"

    /**
     * The card system's own OAuth entry point, as the original named it.
     *
     * **This is no longer what the app logs in with**, and using it as the CAS `service` was a defect:
     * IDS answers 200 with its 「应用未注册」 page, because the entry point is a *redirector*, not a
     * registered application. It is kept because it is the system's public front door and the starting
     * point of the chain below.
     */
    const val OAUTH_ENTRY = "$ORIGIN/home/openXDOAuth2Page"

    /** Where the card system's OAuth chain ends: the account page that carries the handle. */
    const val OAUTH_CALLBACK = "$ORIGIN/home/openHomePage"

    /**
     * The card system's registered app id on the campus app platform (`xxcapp`).
     *
     * Read off the live OAuth chain — the card entry point redirects to `xxcapp` with this id — rather
     * than invented, and it is the one value here that cannot be derived from anything else.
     */
    const val APP_ID = "200200923164459783"

    /** The campus app platform that brokers the card system's login. */
    const val CAMPUS_APP = "https://xxcapp.xidian.edu.cn"

    /**
     * The CAS `service` this app must log in for: the campus app's own CAS entry point for the card.
     *
     * Built rather than pasted, because the live value is a URL nested three deep and one wrong escape
     * makes IDS serve the login page for a *different* application — which fails later, somewhere else,
     * as a missing `openid`. Each layer is escaped exactly once, and
     * `SchoolCardEndpointsTest` pins the result against the string the live chain produced.
     *
     * Measured against the live server: with this service, `/authserver/login` answers the real login
     * form (`pwdEncryptSalt`, `execution`, `lt` all present); with [OAUTH_ENTRY] it answers 「应用未注册」.
     */
    val CAS_LOGIN_TARGET: String
        get() {
            val oauth = "$CAMPUS_APP//uc/api/oauth/index" +
                    "?redirect=${OAUTH_CALLBACK.encodeURLParameter()}" +
                    "&appid=$APP_ID" +
                    "&state=STATE"
            return "$CAMPUS_APP/a_xidian/api/cas-login/index" +
                    "?redirect=${oauth.encodeURLParameter()}" +
                    "&from=wap"
        }

    /** The account page the balance is scraped from. */
    const val MY_ACCOUNT = "$ORIGIN/myaccount/openMyAccount"

    /** The transaction query. A POST, with the range and the `openid` in the body. */
    const val SELF_TRADE_LIST = "$ORIGIN/selftrade/queryCardSelfTradeList"
}
