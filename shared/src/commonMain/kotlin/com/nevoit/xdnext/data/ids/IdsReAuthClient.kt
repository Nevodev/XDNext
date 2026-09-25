package com.nevoit.xdnext.data.ids

import com.nevoit.xdnext.data.net.resolveUrl
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.serialization.json.JsonObject

/**
 * Drives one second-factor challenge.
 *
 * Ported from `ids_reauth_client.dart`. The original reached for a process-global
 * `activeIDSReAuthHandler` and a `BuildContext` to get a dialog on screen; this class has no idea a UI
 * exists, and the dialog is supplied through [IdsReAuthHandler] instead. That is what makes the flow
 * testable and what stops a background session refresh from being unable to authenticate.
 *
 * The server tracks "which factor is selected" per challenge, so [prepare] must run before a code is
 * sent or submitted. Its laziness means the normal path — pick the default factor, send, submit —
 * issues the type change exactly once.
 */
class IdsReAuthClient(
    private val api: IdsAuthApi,
    val challengeUri: String,
    private val username: String,
    private val service: String?,
    private val registerBrowserFingerprint: suspend () -> Unit,
) {

    /** Human-readable recipient the server reported, e.g. a masked phone number. */
    var recipientDescription: String? = null
        private set

    private var deliveryUsername: String? = null
    private var challengePrepared = false
    private var preparedCodeType: IdsReAuthCodeType? = null

    /** The server may mark a challenge as not multifactor; the flag has to be echoed back verbatim. */
    private val isMultifactor: String
        get() = runCatching { Url(challengeUri).parameters["isMultifactor"] }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: "true"

    /** Opens the challenge and selects [codeType]. Idempotent. */
    suspend fun prepare(codeType: IdsReAuthCodeType) {
        if (!challengePrepared) {
            val response = api.fetchChallenge(challengeUri)
            if (response.status != HttpStatusCode.OK) {
                throw IdsReAuthExpiredException("二次认证已失效，请重新登录")
            }
            // The challenge page embeds the identifier the code must be sent to; it can differ from
            // the account name used for the password login.
            deliveryUsername = parseReAuthUserId(response.bodyAsText())
            registerBrowserFingerprint()
            challengePrepared = true
        }

        if (preparedCodeType == codeType) return

        val json = api.changeReAuthType(
            isMultifactor = isMultifactor,
            codeType = codeType,
            service = service,
        )
        if (json.stringOrNull("code") != "1") {
            throw IdsProtocolException(
                json.stringOrNull("message") ?: "无法切换二次认证方式",
            )
        }
        (json["data"] as? JsonObject)
            ?.stringOrNull("reAuthUserNameInput")
            ?.takeIf { it.isNotEmpty() }
            ?.let { recipientDescription = it }

        preparedCodeType = codeType
    }

    /** Asks the server to deliver a code through [codeType]. */
    suspend fun sendCode(codeType: IdsReAuthCodeType): IdsCodeDelivery {
        prepare(codeType)
        return parseIdsCodeDelivery(
            api.sendReAuthDynamicCode(
                userName = deliveryUsername ?: username,
                codeType = codeType,
            ),
        )
    }

    /**
     * Submits [code] and, on success, fetches the service ticket the login flow was waiting for.
     *
     * @return the absolute `Location` the login flow should resume from.
     */
    suspend fun submitCode(
        codeType: IdsReAuthCodeType,
        code: String,
        trustDevice: Boolean,
    ): String {
        prepare(codeType)

        val normalized = code.trim()
        if (normalized.isEmpty()) throw IdsReAuthCodeRejectedException("请输入验证码")

        val result = parseIdsReAuthSubmit(
            api.submitReAuth(
                service = service,
                codeType = codeType,
                isMultifactor = isMultifactor,
                code = normalized,
                trustDevice = trustDevice,
            ),
        )
        when (result.status) {
            IdsReAuthSubmitStatus.Failed -> throw IdsReAuthCodeRejectedException(result.message)
            IdsReAuthSubmitStatus.Unauthorized -> throw IdsReAuthExpiredException(result.message)
            IdsReAuthSubmitStatus.Success -> Unit
        }

        // Passing the second factor is not the same as being logged in: the service ticket still has to
        // be collected, and a challenge can reappear here.
        val loginResponse = api.fetchLoginPage(service)
        val location = loginResponse.headers[HttpHeaders.Location]
        if (loginResponse.status.value !in 300..399 || location == null) {
            throw IdsProtocolException("二次认证成功，但没有收到业务系统登录票据")
        }
        if (isIdsReAuthLocation(location)) {
            throw IdsReAuthExpiredException("二次认证未完成，请重新登录")
        }
        return resolveUrl(IdsEndpoints.LOGIN, location)
    }
}

/**
 * The challenge page carries its identifier as a JSON fragment rather than a field.
 *
 * Regex-scraped because the fragment sits inside a script block, exactly as the original did.
 */
private val RE_AUTH_USER_ID = Regex("\"reAuthUserId\"\\s*:\\s*\"([^\"\\\\]+)\"")

internal fun parseReAuthUserId(body: String): String? =
    RE_AUTH_USER_ID.find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
