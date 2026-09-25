package com.nevoit.xdnext.data.ids

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element

/**
 * The hidden fields the CAS login form requires.
 *
 * The original scavenged these by hand: it took every `<input type="hidden">` and then looked up
 * `pwdEncryptSalt` by id and `lt`/`execution` by name-or-id. It needed `lt` and `execution` for the
 * POST body and `pwdEncryptSalt` as the AES key.
 */
data class IdsLoginForm(
    val lt: String,
    val execution: String,
    val passwordEncryptSalt: String,
)

/**
 * Parses the IDS login page.
 *
 * Kept separate from the HTTP layer so it can be tested against captured HTML without a server — the
 * original had no such seam and no tests for this file, which is exactly why a markup change used to
 * break login silently.
 */
object IdsLoginPageParser {

    /** Returns the login form, or null when the page is not a login form. */
    fun parse(html: String): IdsLoginForm? {
        val document = Ksoup.parse(html)
        return parse(document)
    }

    fun parse(document: Document): IdsLoginForm? {
        val hidden = document.getElementsByTag("input")
            .filter { it.attr("type").equals("hidden", ignoreCase = true) }

        val salt = hidden.valueOf(byIdOrName = "pwdEncryptSalt") ?: return null
        val lt = hidden.valueOf(byIdOrName = "lt") ?: return null
        val execution = hidden.valueOf(byIdOrName = "execution") ?: return null

        return IdsLoginForm(lt = lt, execution = execution, passwordEncryptSalt = salt)
    }

    /**
     * Names of the required hidden fields the page does not carry.
     *
     * Exists so the failure names the field it could not find. The first version of this parser
     * rejected empty values as well as absent ones, and the live server sends `lt` **empty** — so the
     * only symptom was a generic "could not parse the login page" with nothing to go on.
     */
    fun missingFields(html: String): List<String> {
        val hidden = Ksoup.parse(html).getElementsByTag("input")
            .filter { it.attr("type").equals("hidden", ignoreCase = true) }
        return REQUIRED_FIELDS.filter { hidden.valueOf(it) == null }
    }

    /**
     * Extracts the human-readable failure reason from a rejected login page.
     *
     * The original read `#showErrorTip` and then rewrote any message matching `(用户名|密码).*误`
     * into a short "用户名或密码有误", because the server's own text points at a password-recovery
     * button that this app does not have.
     */
    fun parseErrorTip(html: String): String? {
        val document = Ksoup.parse(html)
        val tip = document.getElementById("showErrorTip")?.text()?.trim()
        if (tip.isNullOrEmpty()) return null
        return if (PASSWORD_ERROR.containsMatchIn(tip)) "用户名或密码有误" else tip
    }

    /**
     * Finds the `#continue` form the server sometimes returns instead of a redirect.
     *
     * The original parsed this form's inputs and re-posted them to the same login URL, so a login can
     * legitimately take two POSTs.
     */
    fun parseContinueForm(html: String): Map<String, String>? {
        val document = Ksoup.parse(html)
        // The original also checked that this node was a <form>; looking the id up is sufficient and
        // avoids depending on a node-name accessor whose spelling differs between jsoup and Ksoup.
        val form = document.getElementById("continue") ?: return null
        val fields = mutableMapOf<String, String>()
        for (input in form.getElementsByTag("input")) {
            val name = input.attr("name")
            val value = input.attr("value")
            if (name.isNotEmpty() && value.isNotEmpty()) fields[name] = value
        }
        return fields.ifEmpty { null }
    }

    /** True when the response body looks like an IDS login page rather than a success redirect. */
    fun looksLikeLoginPage(html: String): Boolean =
        html.contains("pwdEncryptSalt", ignoreCase = true)

    /**
     * True when IDS answered with its "target application is not registered" page.
     *
     * A `service` URL that is not in IDS's application registry gets **HTTP 200** and a page saying
     * 应用未注册 — *not* a login form. Nothing about that response is a parse failure, and reporting it
     * as one ("无法解析统一认证登录页（缺少字段：…）") sends whoever reads the log looking for markup
     * changes in a page that was never served.
     *
     * Measured against the live server: probing `/authserver/login?service=<a URL IDS does not know>`
     * returns this page at 200 with the phrase in the body.
     */
    fun isApplicationNotRegistered(html: String): Boolean =
        html.contains("应用未注册") || html.contains("不允许使用认证服务来认证您访问的目标应用")

    /**
     * The page's `<title>`, for a log line that says what came back instead of only what was missing.
     *
     * IDS ships some of its error pages as **GBK** while declaring UTF-8 in the markup, so a body
     * decoded as UTF-8 reads as mojibake. The title is matched after a lossy decode as well, which is
     * enough to identify the page even when the bytes are not what the content type claims.
     */
    fun pageTitle(html: String): String? =
        TITLE.find(html)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * True when the server's rejection is about the captcha rather than the credentials.
     *
     * Load bearing, and confirmed against the live server: a missing or wrong slider captcha is
     * rejected with **HTTP 401** carrying `图形动态验证码错误`. Treating every 401 as "wrong password"
     * would tell a user with perfectly good credentials that their password is bad.
     */
    fun isCaptchaError(tip: String): Boolean = tip.contains("验证码")

    /**
     * Reads a hidden field's value by id or name.
     *
     * Only the *presence* of the `value` attribute is required, not a non-empty value. This is
     * deliberate and is how the original behaved: the live IDS login page ships
     * `<input type="hidden" name="lt" value="" />` with an empty `lt`, and treating that as "missing"
     * broke login entirely.
     */
    private fun List<Element>.valueOf(byIdOrName: String): String? =
        firstOrNull { it.attr("id") == byIdOrName || it.attr("name") == byIdOrName }
            ?.takeIf { it.hasAttr("value") }
            ?.attr("value")

    private val REQUIRED_FIELDS = listOf("lt", "execution", "pwdEncryptSalt")

    private val PASSWORD_ERROR = Regex("(用户名|密码).*误", RegexOption.DOT_MATCHES_ALL)

    private val TITLE =
        Regex("<title>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
}
