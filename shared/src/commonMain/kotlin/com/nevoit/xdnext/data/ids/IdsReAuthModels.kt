package com.nevoit.xdnext.data.ids

import com.nevoit.xdnext.data.net.resolveUrl
import io.ktor.http.Url
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Second-factor (二次认证) protocol model and parsers.
 *
 * Ported from `ids_auth_protocol.dart` and the top of `ids_reauth_client.dart`. The parsers are pure
 * functions on purpose: every one of these string codes is a silent failure if it is wrong, and the
 * original had nothing testing them.
 */

/** Which second factor the user asked for. The wire values are positional, not semantic. */
enum class IdsReAuthCodeType(
    val reAuthType: String,
    val authCodeTypeName: String,
) {
    /** SMS to the number on file. */
    Sms(reAuthType = "3", authCodeTypeName = "reAuthDynamicCodeType"),

    /** A code delivered through enterprise WeChat. */
    EnterpriseWeChat(reAuthType = "4", authCodeTypeName = "reAuthWChatDynamicCodeType"),
}

/** Outcome of submitting a second-factor code. */
enum class IdsReAuthSubmitStatus { Success, Failed, Unauthorized }

private const val CODE_SUCCESS = "reAuth_success"
private const val CODE_FAILED = "reAuth_failed"
private const val CODE_UNAUTHORIZED = "reAuth_unauthorized"

data class IdsReAuthSubmitResult(
    val status: IdsReAuthSubmitStatus,
    val message: String,
)

/**
 * Parses the response to `reAuthSubmit.do`.
 *
 * An unrecognised `code` is a protocol error rather than a failure: treating it as "wrong code
 * entered" would loop the user on a payload the client does not understand.
 */
fun parseIdsReAuthSubmit(json: JsonObject): IdsReAuthSubmitResult {
    val code = json.stringOrNull("code")
    val message = json.stringOrNull("msg") ?: "二次认证失败"
    val status = when (code) {
        CODE_SUCCESS -> IdsReAuthSubmitStatus.Success
        CODE_FAILED -> IdsReAuthSubmitStatus.Failed
        CODE_UNAUTHORIZED -> IdsReAuthSubmitStatus.Unauthorized
        else -> throw IdsProtocolException("统一认证返回了未知的二次认证状态：$code")
    }
    return IdsReAuthSubmitResult(status = status, message = message)
}

/** What the server reports after being asked to send a code. */
data class IdsCodeDelivery(
    val message: String,
    val maskedMobile: String?,
    val retryAfterSeconds: Int,
    /** True when the server refused to resend because a code is already outstanding. */
    val alreadySent: Boolean,
)

private val ACCEPTED_DELIVERY_RESULTS = setOf(
    "success",
    "other_success",
    "wechat_success",
    // Not really a success: the previous code is still valid, so the UI should just make the user wait.
    "code_time_fail",
)

/**
 * Parses the response to `getDynamicCodeByReauth.do`.
 *
 * `codeTime` is a *string* on the wire and is occasionally absent; when it is, WeChat deliveries still
 * get a sensible retry window and SMS ones get none, matching the original.
 */
fun parseIdsCodeDelivery(json: JsonObject): IdsCodeDelivery {
    val result = json.stringOrNull("res")
    if (result == null || result !in ACCEPTED_DELIVERY_RESULTS) {
        throw IdsProtocolException(json.stringOrNull("returnMessage") ?: "验证码发送失败")
    }

    val reported = json.stringOrNull("codeTime")?.toIntOrNull()
    val seconds = if (reported == null || reported < 0) {
        if (result == "wechat_success") 120 else 0
    } else {
        reported
    }

    val mobile = json.stringOrNull("mobile")?.takeIf { it.isNotEmpty() }

    return IdsCodeDelivery(
        message = json.stringOrNull("returnMessage") ?: "验证码已发送",
        maskedMobile = mobile?.let(::maskIdsPhoneNumber),
        retryAfterSeconds = seconds,
        alreadySent = result == "code_time_fail",
    )
}

/**
 * Masks a phone number for display: `138****8000`.
 *
 * Anything shorter than seven characters becomes a bare `****` rather than leaking a short number in
 * full — the original drew the same line.
 */
fun maskIdsPhoneNumber(value: String): String {
    if (value.length < 7) return "****"
    return value.take(3) + "****" + value.takeLast(4)
}

/**
 * True when a URL points at the second-factor challenge page.
 *
 * Matched on protocol, host and path rather than by string containment, so a `service=` parameter
 * that happens to embed the path cannot be mistaken for a real challenge.
 */
fun isIdsReAuthLocation(location: String, base: String = IdsEndpoints.IDS_ORIGIN): Boolean {
    val absolute = resolveUrl(base, location)
    val url = runCatching { Url(absolute) }.getOrNull() ?: return false
    return url.protocol.name == "https" &&
            url.host == "ids.xidian.edu.cn" &&
            url.encodedPath == "/authserver/reAuthCheck/reAuthLoginView.do"
}

/**
 * Resumes an interrupted login once the user has passed the second factor.
 *
 * The repository takes one of these. When it is null a challenge is reported as
 * [IdsReAuthRequiredException] rather than being silently skipped. See
 * [com.nevoit.xdnext.data.session.IdsReAuthBroker] for the implementation that shows a dialog.
 *
 * @return the `Location` the login flow should continue from.
 */
fun interface IdsReAuthHandler {
    suspend fun handle(client: IdsReAuthClient): String
}

internal fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull
