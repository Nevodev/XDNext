package com.nevoit.xdnext.data.ids

/**
 * Endpoints and constants for Xidian's IDS (统一认证服务) CAS server.
 *
 * Ported from `lib/repository/ids_session/` and `lib/repository/network_client.dart`.
 */
object IdsEndpoints {

    const val IDS_ORIGIN = "https://ids.xidian.edu.cn"

    const val AUTH_SERVER = "$IDS_ORIGIN/authserver"

    /** The CAS login page. A 3xx here means the persisted session is still valid. */
    const val LOGIN = "$AUTH_SERVER/login"

    /** Registers the trusted-device fingerprint. Called before every password login. */
    const val BROWSER_FINGERPRINT = "$AUTH_SERVER/bfp/info"

    /** Primes the slider captcha and returns its images. */
    const val OPEN_SLIDER_CAPTCHA = "$AUTH_SERVER/common/openSliderCaptcha.htl"

    /** Submits a solved slider captcha. */
    const val VERIFY_SLIDER_CAPTCHA = "$AUTH_SERVER/common/verifySliderCaptcha.htl"

    /** Second-factor challenge paths. */
    const val RE_AUTH_LOGIN_VIEW = "$AUTH_SERVER/reAuthCheck/reAuthLoginView.do"
    const val CHANGE_RE_AUTH_TYPE = "$AUTH_SERVER/reAuthCheck/changeReAuthType.do"
    const val GET_DYNAMIC_CODE = "$AUTH_SERVER/dynamicCode/getDynamicCodeByReauth.do"
    const val RE_AUTH_SUBMIT = "$AUTH_SERVER/reAuthCheck/reAuthSubmit.do"

    /**
     * The eHall service used for the automatic login on app start.
     *
     * This is the target the original used in `HomepageController._comboLogin`, and it is what makes
     * the session reusable for the business modules that come later.
     */
    const val EHALL_SERVICE =
        "https://ehall.xidian.edu.cn/login?service=https://ehall.xidian.edu.cn/new/index.html"

    /**
     * The service used to decide whether the account is a postgraduate.
     *
     * The original calls `getCanVisitAppList.do` on this host afterwards and stores the answer as the
     * `role` preference; that decision changes which parser every academic module uses, so it is part
     * of login rather than of any single feature.
     */
    const val YJSPT_SERVICE =
        "https://yjspt.xidian.edu.cn/gsapp/sys/yjsemaphome/portal/index.do"

    const val YJSPT_CAN_VISIT_APP_LIST =
        "https://yjspt.xidian.edu.cn/gsapp/sys/yjsemaphome/modules/pubWork/getCanVisitAppList.do"

    /**
     * User agent. The original sent a desktop Chrome UA with an `XDYou/2.0.0` suffix; a plain JDK
     * user agent is measurably more likely to be treated as a bot by the campus WAF.
     */
    const val USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 XDNext/0.1.0"

    /** The original allowed 30 hops before giving up on the CAS redirect chain. */
    const val MAX_AUTH_REDIRECTS = 30
}
