package com.nevoit.xdnext.data.ids

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the second-factor protocol and the captcha payload shape.
 *
 * Every value here is a magic string the server matches exactly, and the original had no tests for any
 * of them — a typo in a `reAuthType` would have surfaced only as "二次认证失败" against a live server.
 */
class IdsReAuthProtocolTest {

    private fun json(text: String): JsonObject =
        Json.parseToJsonElement(text) as JsonObject

    // --- reAuthSubmit ---------------------------------------------------------------------------

    @Test
    fun mapsTheThreeSubmissionCodes() {
        assertEquals(
            IdsReAuthSubmitStatus.Success,
            parseIdsReAuthSubmit(json("""{"code":"reAuth_success","msg":"ok"}""")).status,
        )
        assertEquals(
            IdsReAuthSubmitStatus.Failed,
            parseIdsReAuthSubmit(json("""{"code":"reAuth_failed","msg":"验证码错误"}""")).status,
        )
        assertEquals(
            IdsReAuthSubmitStatus.Unauthorized,
            parseIdsReAuthSubmit(json("""{"code":"reAuth_unauthorized","msg":"已过期"}""")).status,
        )
    }

    @Test
    fun carriesTheServerMessageThrough() {
        val result = parseIdsReAuthSubmit(json("""{"code":"reAuth_failed","msg":"验证码错误"}"""))
        assertEquals("验证码错误", result.message)
    }

    @Test
    fun rejectsAnUnknownSubmissionCodeInsteadOfTreatingItAsAWrongCode() {
        // Treating an unknown code as "wrong code entered" would loop the user forever on a payload the
        // client cannot understand.
        assertFailsWith<IdsProtocolException> {
            parseIdsReAuthSubmit(json("""{"code":"something_new","msg":"?"}"""))
        }
    }

    @Test
    fun fallsBackToADefaultMessageWhenNoneIsGiven() {
        assertEquals(
            "二次认证失败",
            parseIdsReAuthSubmit(json("""{"code":"reAuth_failed"}""")).message,
        )
    }

    // --- code delivery --------------------------------------------------------------------------

    @Test
    fun parsesAnSmsDeliveryWithItsCountdown() {
        val delivery = parseIdsCodeDelivery(
            json("""{"res":"success","returnMessage":"验证码已发送","codeTime":"60","mobile":"13800138000"}"""),
        )
        assertEquals("验证码已发送", delivery.message)
        assertEquals(60, delivery.retryAfterSeconds)
        assertEquals("138****8000", delivery.maskedMobile)
        assertFalse(delivery.alreadySent)
    }

    @Test
    fun treatsAnAlreadySentCodeAsADeliveryRatherThanAFailure() {
        // The previous code is still valid, so the UI should make the user wait, not show an error.
        val delivery = parseIdsCodeDelivery(
            json("""{"res":"code_time_fail","returnMessage":"请稍后再试","codeTime":"45"}"""),
        )
        assertTrue(delivery.alreadySent)
        assertEquals(45, delivery.retryAfterSeconds)
    }

    @Test
    fun givesWechatDeliveriesADefaultCountdownWhenTheServerOmitsOne() {
        val delivery = parseIdsCodeDelivery(
            json("""{"res":"wechat_success","returnMessage":"已发送"}"""),
        )
        assertEquals(120, delivery.retryAfterSeconds)
        assertNull(delivery.maskedMobile)
    }

    @Test
    fun treatsANegativeCountdownAsAbsent() {
        val delivery = parseIdsCodeDelivery(
            json("""{"res":"success","returnMessage":"已发送","codeTime":"-1"}"""),
        )
        assertEquals(0, delivery.retryAfterSeconds)
    }

    @Test
    fun rejectsAFailedDeliveryWithTheServerReason() {
        val failure = assertFailsWith<IdsProtocolException> {
            parseIdsCodeDelivery(json("""{"res":"fail","returnMessage":"手机号未绑定"}"""))
        }
        assertEquals("手机号未绑定", failure.message)
    }

    // --- phone masking --------------------------------------------------------------------------

    @Test
    fun masksPhoneNumbers() {
        assertEquals("138****8000", maskIdsPhoneNumber("13800138000"))
        assertEquals("186****1234", maskIdsPhoneNumber("18612341234"))
    }

    @Test
    fun doesNotLeakShortNumbers() {
        // Anything too short to mask meaningfully becomes a bare placeholder.
        assertEquals("****", maskIdsPhoneNumber("123456"))
        assertEquals("****", maskIdsPhoneNumber(""))
    }

    // --- challenge location ---------------------------------------------------------------------

    @Test
    fun recognisesTheChallengePage() {
        assertTrue(
            isIdsReAuthLocation(
                "https://ids.xidian.edu.cn/authserver/reAuthCheck/reAuthLoginView.do?service=x",
            ),
        )
        assertTrue(isIdsReAuthLocation("/authserver/reAuthCheck/reAuthLoginView.do"))
    }

    @Test
    fun rejectsALookalikeChallengeOnAnotherHost() {
        // Matched on host and path, so a `service=` parameter embedding the path cannot fake it.
        assertFalse(
            isIdsReAuthLocation(
                "https://evil.example.com/authserver/reAuthCheck/reAuthLoginView.do",
            ),
        )
        assertFalse(
            isIdsReAuthLocation(
                "https://ids.xidian.edu.cn/authserver/login?service=" +
                        "https://x/authserver/reAuthCheck/reAuthLoginView.do",
            ),
        )
    }

    @Test
    fun requiresHttpsForTheChallenge() {
        assertFalse(
            isIdsReAuthLocation("http://ids.xidian.edu.cn/authserver/reAuthCheck/reAuthLoginView.do"),
        )
    }

    // --- wire values ----------------------------------------------------------------------------

    @Test
    fun pinsTheWireValuesForEachFactor() {
        // These are positional codes, not semantics; a swap silently routes SMS codes to WeChat.
        assertEquals("3", IdsReAuthCodeType.Sms.reAuthType)
        assertEquals("reAuthDynamicCodeType", IdsReAuthCodeType.Sms.authCodeTypeName)
        assertEquals("4", IdsReAuthCodeType.EnterpriseWeChat.reAuthType)
        assertEquals(
            "reAuthWChatDynamicCodeType",
            IdsReAuthCodeType.EnterpriseWeChat.authCodeTypeName
        )
    }

    @Test
    fun parsesTheChallengeUserIdOutOfTheEmbeddedFragment() {
        val page = """<script>var cfg = {"reAuthUserId" : "20210001", "other": 1};</script>"""
        assertEquals("20210001", parseReAuthUserId(page))
        assertNull(parseReAuthUserId("<html>no id here</html>"))
        assertNull(parseReAuthUserId("""{"reAuthUserId":""}"""))
    }

    // --- captcha payload ------------------------------------------------------------------------

    @Test
    fun buildsTheExactCaptchaPayloadTheServerExpects() {
        // Pinned because the server decrypts this and parses it; a renamed key would be rejected with
        // no diagnostic beyond "captcha failed".
        val tracks = listOf(
            CaptchaTrackPoint(0, 0, 0),
            CaptchaTrackPoint(100, 2, 420),
        )
        assertEquals(
            """{"canvasLength":280,"moveLength":100,"tracks":[""" +
                    """{"a":0,"b":0,"c":0},{"a":100,"b":2,"c":420}]}""",
            buildCaptchaPayload(tracks, canvasLength = 280),
        )
    }

    @Test
    fun usesTheLastPointAsTheMoveLength() {
        val tracks = listOf(CaptchaTrackPoint(5, 0, 10), CaptchaTrackPoint(237, -3, 500))
        val payload = buildCaptchaPayload(tracks, canvasLength = 280)
        assertTrue(payload.contains("\"moveLength\":237"), payload)
    }

    @Test
    fun handlesAnEmptyTrackList() {
        assertEquals(
            """{"canvasLength":280,"moveLength":0,"tracks":[]}""",
            buildCaptchaPayload(emptyList(), canvasLength = 280),
        )
    }
}
