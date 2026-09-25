package com.nevoit.xdnext.data.ids

import com.nevoit.xdnext.core.log.appLog
import com.nevoit.xdnext.core.platform.currentTimeMillis
import com.nevoit.xdnext.data.net.FileCookieStorage
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The HTTP surface of Xidian's IDS CAS server.
 *
 * This class performs requests and nothing else: no state machine, no retries, no interpretation of
 * what a redirect means. [IdsSessionRepository] owns the flow. The original mixed all three into one
 * 576-line file, which made the protocol impossible to test without a live server.
 *
 * Every cookie is carried by the shared persistent store, so unlike the original there is no need to
 * hand-assemble a `Cookie` header — the slider-captcha endpoints are on the same host and the client
 * already attaches the session cookie.
 */
@OptIn(ExperimentalEncodingApi::class)
class IdsAuthApi(
    private val client: HttpClient,
    private val cookieStorage: FileCookieStorage,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {

    /**
     * Fetches the login page.
     *
     * A 3xx response is the *success* case for automatic login: it means the persisted cookie was
     * still valid and the server is redirecting straight to the service with a fresh ticket.
     */
    suspend fun fetchLoginPage(service: String?): HttpResponse =
        client.get(IdsEndpoints.LOGIN) {
            if (service != null) url { parameters.append("service", service) }
        }

    /** GETs an absolute URI without following redirects. Used by the redirect walker. */
    suspend fun fetch(uri: String): HttpResponse = client.get(uri)

    /**
     * Registers the trusted-device fingerprint.
     *
     * The original called this before every password login so the server could mark the device as
     * known and (with luck) skip second-factor challenges.
     */
    suspend fun registerBrowserFingerprint(fingerprint: String) {
        val response = client.get(IdsEndpoints.BROWSER_FINGERPRINT) {
            url {
                parameters.append("bfp", fingerprint)
                parameters.append("_", currentTimeMillis().toString())
            }
        }
        if (!response.status.isSuccess()) {
            // Not fatal: the original logged and continued, and login still works without it.
            appLog.w { "Browser fingerprint registration returned ${response.status}" }
        }
    }

    /**
     * Primes the slider captcha session.
     *
     * The original issued this GET immediately before invoking its solver, so the server-side captcha
     * state exists by the time the solver asks for the images.
     */
    suspend fun primeSliderCaptcha() {
        client.get(IdsEndpoints.OPEN_SLIDER_CAPTCHA) {
            url { parameters.append("_", currentTimeMillis().toString()) }
        }
    }

    /**
     * Downloads a slider captcha challenge.
     *
     * The response carries two base64 PNGs. Critically, the AES key for the signed answer is the
     * **last 16 bytes of the piece image**, so the raw bytes are kept rather than only the decoded
     * bitmap.
     */
    suspend fun fetchSliderCaptcha(): SliderCaptchaChallenge? {
        val response = client.get(IdsEndpoints.OPEN_SLIDER_CAPTCHA) {
            url { parameters.append("_", currentTimeMillis().toString()) }
        }
        if (!response.status.isSuccess()) {
            appLog.w { "Slider captcha fetch returned ${response.status}" }
            return null
        }

        val root = response.bodyAsText().toJsonObjectOrNull() ?: return null
        val puzzleBase64 = root.stringOrNull("bigImage") ?: return null
        val pieceBase64 = root.stringOrNull("smallImage") ?: return null

        val piece = runCatching { Base64.decode(pieceBase64) }.getOrNull() ?: return null
        val puzzle = runCatching { Base64.decode(puzzleBase64) }.getOrNull() ?: return null
        if (piece.size < 16) {
            appLog.w { "Slider captcha piece image is too short to carry a key (${piece.size} bytes)" }
            return null
        }

        return SliderCaptchaChallenge(
            puzzleImage = puzzle,
            pieceImage = piece,
            pieceKey = piece.copyOfRange(piece.size - 16, piece.size),
        )
    }

    /**
     * Submits a signed captcha solution.
     *
     * The server signals success with `errorCode == 1`, not with an HTTP status — the original relied
     * on the same field, so it is checked explicitly rather than trusting 200.
     */
    suspend fun submitSliderCaptcha(sign: String): Boolean {
        val response = client.submitForm(
            url = IdsEndpoints.VERIFY_SLIDER_CAPTCHA,
            formParameters = Parameters.build { append("sign", sign) },
        ) {
            header(HttpHeaders.Accept, "application/json, text/javascript, */*; q=0.01")
            header(HttpHeaders.Origin, IdsEndpoints.IDS_ORIGIN)
            header("X-Requested-With", "XMLHttpRequest")
        }
        if (!response.status.isSuccess()) {
            appLog.w { "Slider captcha submit returned ${response.status}" }
            return false
        }
        val root = response.bodyAsText().toJsonObjectOrNull() ?: return false
        return root["errorCode"]?.jsonPrimitive?.contentOrNull == "1"
    }

    /**
     * Posts the login form.
     *
     * [fields] must already contain the AES-encrypted password and the hidden `lt`/`execution` values.
     */
    suspend fun postLogin(fields: Map<String, String>, service: String?): HttpResponse =
        client.submitForm(
            url = IdsEndpoints.LOGIN,
            formParameters = Parameters.build { fields.forEach { (k, v) -> append(k, v) } },
        ) {
            if (service != null) url { parameters.append("service", service) }
        }

    /** Posts the `#continue` form the server sometimes returns instead of a redirect. */
    suspend fun postContinueForm(fields: Map<String, String>): HttpResponse =
        client.submitForm(
            url = IdsEndpoints.LOGIN,
            formParameters = Parameters.build { fields.forEach { (k, v) -> append(k, v) } },
        )

    /**
     * Asks the postgraduate system whether this account may see its applications.
     *
     * The original used the answer to decide between the undergraduate (eHall) and postgraduate
     * (yjspt) parsers for every academic module, so this is part of login rather than of any one
     * feature. A missing `res` means undergraduate.
     */
    suspend fun probePostgraduate(): Boolean {
        val response = client.post(IdsEndpoints.YJSPT_CAN_VISIT_APP_LIST)
        val root = response.bodyAsText().toJsonObjectOrNull() ?: return false
        val res = root["res"] ?: return false
        return res !is JsonNull
    }

    /** The raw `Cookie` header for the IDS host, for callers that bypass the shared client. */
    suspend fun idsCookieHeader(): String =
        cookieStorage.cookieHeaderFor(Url(IdsEndpoints.IDS_ORIGIN))

    /** Drops all cookies. The original did this before every fresh manual login. */
    suspend fun clearCookies() = cookieStorage.clearAll()

    // ---------------------------------------------------------------------------------------------
    // Second factor (二次认证)
    // ---------------------------------------------------------------------------------------------

    /** GETs the challenge page. A non-200 here means the challenge has already expired. */
    suspend fun fetchChallenge(uri: String): HttpResponse = client.get(uri)

    /**
     * Tells the server which second factor the user picked.
     *
     * This has to happen before a code is requested or submitted, because the server tracks the chosen
     * type per challenge.
     */
    suspend fun changeReAuthType(
        isMultifactor: String,
        codeType: IdsReAuthCodeType,
        service: String?,
    ): JsonObject = postForJson(
        url = IdsEndpoints.CHANGE_RE_AUTH_TYPE,
        fields = mapOf(
            "isMultifactor" to isMultifactor,
            "reAuthType" to codeType.reAuthType,
            "service" to (service ?: ""),
        ),
    )

    /** Asks the server to deliver a code. */
    suspend fun sendReAuthDynamicCode(
        userName: String,
        codeType: IdsReAuthCodeType,
    ): JsonObject = postForJson(
        url = IdsEndpoints.GET_DYNAMIC_CODE,
        fields = mapOf(
            "userName" to userName,
            "authCodeTypeName" to codeType.authCodeTypeName,
        ),
    )

    /**
     * Submits a second-factor code.
     *
     * The original always sent the same eleven fields, most of them empty: the server rejects a
     * request with a missing field even when that field is unused for the chosen factor, so they are
     * all reproduced.
     */
    suspend fun submitReAuth(
        service: String?,
        codeType: IdsReAuthCodeType,
        isMultifactor: String,
        code: String,
        trustDevice: Boolean,
    ): JsonObject = postForJson(
        url = IdsEndpoints.RE_AUTH_SUBMIT,
        fields = mapOf(
            "service" to (service ?: ""),
            "reAuthType" to codeType.reAuthType,
            "isMultifactor" to isMultifactor,
            "password" to "",
            "dynamicCode" to code,
            "uuid" to "",
            "answer1" to "",
            "answer2" to "",
            "otpCode" to "",
            "skipTmpReAuth" to trustDevice.toString(),
        ),
    )

    /**
     * POSTs form fields and requires a JSON object back.
     *
     * The reAuth endpoints answer with JSON, but a session that expired mid-challenge answers with an
     * HTML login page; that is reported as a protocol error rather than being parsed into nonsense.
     */
    private suspend fun postForJson(url: String, fields: Map<String, String>): JsonObject {
        val response = client.submitForm(
            url = url,
            formParameters = Parameters.build { fields.forEach { (k, v) -> append(k, v) } },
        )
        val body = response.bodyAsText()
        return body.toJsonObjectOrNull() ?: throw IdsProtocolException("统一认证返回了非 JSON 响应")
    }

    private fun String.toJsonObjectOrNull(): JsonObject? =
        runCatching { json.parseToJsonElement(this) as? JsonObject }.getOrNull()
}
