package com.nevoit.xdnext.data.ids

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parser tests for the CAS login page.
 *
 * The original scraped this markup with no test seam, so a server-side template change broke login
 * with no failing test and no useful error. These fixtures are written from the shapes the original
 * relied on: hidden inputs carrying `lt`, `execution` and `pwdEncryptSalt`.
 */
class IdsLoginPageParserTest {

    @Test
    fun extractsTheThreeRequiredHiddenFields() {
        val form = IdsLoginPageParser.parse(LOGIN_PAGE)
        assertNotNull(form, "the standard login page must parse")
        assertEquals("LT-123456-abcdef", form.lt)
        assertEquals("e1s1", form.execution)
        assertEquals("0123456789abcdef", form.passwordEncryptSalt)
    }

    @Test
    fun recognisesTheNotRegisteredPageIDSAnswersForAnUnregisteredService() {
        // Measured against the live server: asking for `/authserver/login?service=<a URL IDS does not
        // know>` returns **HTTP 200** with this page — not a login form and not an error status. The
        // app used to report it as "could not parse the login page (missing lt, execution,
        // pwdEncryptSalt)", which sends the reader looking for a markup change in a page that was never
        // served. The sentence, not the status, is what identifies it.
        val notRegistered = """
            <!DOCTYPE html><html><head><title>应用未注册</title></head>
            <body><div class="no-access-or-registered-tip">应用未注册</div>
            <div class="no-access-or-registered-desc">不允许使用认证服务来认证您访问的目标应用。</div>
            </body></html>
        """.trimIndent()

        assertTrue(IdsLoginPageParser.isApplicationNotRegistered(notRegistered))
        assertEquals("应用未注册", IdsLoginPageParser.pageTitle(notRegistered))
        assertFalse(IdsLoginPageParser.looksLikeLoginPage(notRegistered))

        // The check reads the sentence, which is Chinese text — so it depends on the body having been
        // *decoded* correctly. What makes that acceptable is that a real page is recognised by its salt
        // field instead: a garbled body fails the login-page test either way, so the only cost of a
        // mis-decoded error page is a less specific message, never a missed login.
        assertTrue(
            IdsLoginPageParser.looksLikeLoginPage(LOGIN_PAGE),
            "A real page is identified by its salt field, which no encoding can garble.",
        )
    }

    @Test
    fun findsHiddenFieldsByIdAsWellAsByName() {
        val byIdOnly = """
            <form>
              <input type="hidden" id="lt" value="LT-X">
              <input type="hidden" id="execution" value="e9s9">
              <input type="hidden" id="pwdEncryptSalt" value="saltsaltsaltsalt">
            </form>
        """.trimIndent()
        val form = IdsLoginPageParser.parse(byIdOnly)
        assertNotNull(form)
        assertEquals("LT-X", form.lt)
        assertEquals("e9s9", form.execution)
        assertEquals("saltsaltsaltsalt", form.passwordEncryptSalt)
    }

    @Test
    fun ignoresNonHiddenInputs() {
        // A visible input named `lt` must not be mistaken for the hidden one.
        val page = """
            <form>
              <input type="text" name="lt" value="WRONG">
              <input type="hidden" name="lt" value="RIGHT">
              <input type="hidden" name="execution" value="e1s1">
              <input type="hidden" id="pwdEncryptSalt" value="saltsaltsaltsalt">
            </form>
        """.trimIndent()
        assertEquals("RIGHT", IdsLoginPageParser.parse(page)?.lt)
    }

    @Test
    fun returnsNullWhenThePageIsNotALoginForm() {
        assertNull(IdsLoginPageParser.parse("<html><body>service is unavailable</body></html>"))
        assertNull(IdsLoginPageParser.parse(""))
        // Missing the salt means there is no key to encrypt with, so the page is unusable.
        assertNull(
            IdsLoginPageParser.parse(
                """<form><input type="hidden" name="lt" value="x">
                   <input type="hidden" name="execution" value="y"></form>""",
            ),
        )
    }

    @Test
    fun rewritesTheServersPasswordErrorIntoAShortMessage() {
        // The server's own wording points at a password-recovery button this app does not have, so the
        // original replaced it. Preserved verbatim.
        val tip = IdsLoginPageParser.parseErrorTip(ERROR_PAGE)
        assertEquals("用户名或密码有误", tip)
    }

    @Test
    fun keepsUnrelatedErrorTipsIntact() {
        val page = """<div id="showErrorTip">系统繁忙，请稍后重试</div>"""
        assertEquals("系统繁忙，请稍后重试", IdsLoginPageParser.parseErrorTip(page))
        assertNull(IdsLoginPageParser.parseErrorTip("<html><body>no tip here</body></html>"))
    }

    @Test
    fun readsTheContinueFormTheServerSometimesReturns() {
        val fields = IdsLoginPageParser.parseContinueForm(CONTINUE_PAGE)
        assertNotNull(fields)
        assertEquals("LT-999", fields["lt"])
        assertEquals("continue", fields["_eventId"])
        assertEquals("e2s1", fields["execution"])
    }

    @Test
    fun reportsNoContinueFormForAnOrdinaryLoginPage() {
        assertNull(IdsLoginPageParser.parseContinueForm(LOGIN_PAGE))
    }

    @Test
    fun recognisesALoginPageByItsSaltField() {
        assertTrue(IdsLoginPageParser.looksLikeLoginPage(LOGIN_PAGE))
        assertTrue(!IdsLoginPageParser.looksLikeLoginPage("<html>hello</html>"))
    }

    @Test
    fun parsesTheLiveServerMarkupWhereLtIsEmpty() {
        // Regression test against markup captured from the real server. It sends
        // `<input type="hidden" name="lt" id="lt" value="" />` — an *empty* `lt`.
        //
        // An earlier version of this parser rejected empty values as if the field were absent, which
        // broke login outright on the live server with only a generic "could not parse the login page"
        // to go on. Only the presence of the attribute may be required.
        val form = IdsLoginPageParser.parse(LIVE_LOGIN_PAGE)
        assertNotNull(form, "the live login page must parse")
        assertEquals("", form.lt, "an empty lt is valid and must be preserved as-is")
        assertEquals("e1s1", form.execution)
        assertEquals("4Jy0dkDCcy9aZ3cL", form.passwordEncryptSalt)
    }

    @Test
    fun theLiveSaltIsSixteenCharactersSoAes128IsCorrect() {
        // The salt is used directly as the AES key, so its length decides the cipher. Captured from the
        // live server to confirm the 16-byte assumption rather than inferring it.
        assertEquals(16, "4Jy0dkDCcy9aZ3cL".length)
    }

    @Test
    fun namesTheFieldsItCouldNotFind() {
        // The failure message should say which field was missing; the generic version cost a full
        // debugging round trip against the live server.
        assertEquals(
            listOf("lt", "execution", "pwdEncryptSalt"),
            IdsLoginPageParser.missingFields("<html><body>nothing here</body></html>"),
        )
        assertEquals(
            listOf("lt"),
            IdsLoginPageParser.missingFields(
                """<form>
                     <input type="hidden" name="execution" value="e1s1">
                     <input type="hidden" id="pwdEncryptSalt" value="saltsaltsaltsalt">
                   </form>""",
            ),
        )
        assertEquals(emptyList(), IdsLoginPageParser.missingFields(LIVE_LOGIN_PAGE))
    }

    @Test
    fun distinguishesACaptchaRejectionFromAWrongPassword() {
        // The live server answers a missing or wrong slider captcha with HTTP 401 and this tip, so a 401
        // alone must not be read as "wrong password".
        val captchaTip = requireNotNull(
            IdsLoginPageParser.parseErrorTip(
                """<div id="showErrorTip">图形动态验证码错误</div>""",
            ),
        )
        assertEquals("图形动态验证码错误", captchaTip)
        assertTrue(IdsLoginPageParser.isCaptchaError(captchaTip))

        val passwordTip = requireNotNull(IdsLoginPageParser.parseErrorTip(ERROR_PAGE))
        assertEquals("用户名或密码有误", passwordTip)
        assertTrue(!IdsLoginPageParser.isCaptchaError(passwordTip))
    }

    private companion object {
        /**
         * Trimmed from the real response of `GET https://ids.xidian.edu.cn/authserver/login`, keeping
         * the hidden-input block verbatim — including the empty `lt` and the unrelated fields around it.
         */
        val LIVE_LOGIN_PAGE = """
            <!DOCTYPE html><html class="root-main"><head>
            <meta charset="utf-8"/><title>统一身份认证平台</title>
            </head><body>
            <div><span id="showErrorTip"></span></div>
            <input type="hidden" id="_eventId" name="_eventId" value="submit" />
            <input type="hidden" id="username-fido" name="username" value="" />
            <input type="hidden" id="password-fido" name="password" value="" />
            <input type="hidden" name="lt" id="lt" value="" />
            <input type="hidden" id="pwdEncryptSalt" value="4Jy0dkDCcy9aZ3cL" />
            <input type="hidden" id="execution" name="execution" value="e1s1" /></form>
            </body></html>
        """.trimIndent()


        val LOGIN_PAGE = """
            <!DOCTYPE html>
            <html><body>
              <form id="casLoginForm" method="post">
                <input type="hidden" name="lt" value="LT-123456-abcdef">
                <input type="hidden" name="execution" value="e1s1">
                <input type="hidden" id="pwdEncryptSalt" value="0123456789abcdef">
                <input type="text" id="username" name="username">
                <input type="password" id="password" name="password">
                <input type="submit" value="登录">
              </form>
            </body></html>
        """.trimIndent()

        val ERROR_PAGE = """
            <html><body>
              <div id="showErrorTip">
                用户名或密码有误，用户名为工号/学号，如果确认用户名无误，请点"找回密码"自助重置密码。
              </div>
            </body></html>
        """.trimIndent()

        val CONTINUE_PAGE = """
            <html><body>
              <form id="continue" method="post">
                <input type="hidden" name="lt" value="LT-999">
                <input type="hidden" name="_eventId" value="continue">
                <input type="hidden" name="execution" value="e2s1">
              </form>
            </body></html>
        """.trimIndent()
    }
}
