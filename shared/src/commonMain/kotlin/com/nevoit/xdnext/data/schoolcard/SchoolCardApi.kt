package com.nevoit.xdnext.data.schoolcard

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.nevoit.xdnext.core.log.appLog
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** The campus card system answered in a way the port does not define. */
class SchoolCardProtocolException(message: String) : Exception(message)

/**
 * The campus card system could not be reached, or the exchange was cut short.
 *
 * The same split the energy module makes: transport failures become this type here so nothing above
 * has to know the HTTP engine's exception hierarchy, which differs per platform.
 */
class SchoolCardNetworkException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * The HTTP surface of the campus card system.
 *
 * Ported from `lib/repository/ids_session/school_card_session.dart`, split the way
 * [com.nevoit.xdnext.data.energy.EnergyApi] is split from its session: this class performs requests
 * and parses what comes back, while [SchoolCardSession] owns the `openid` flow and the retry policy.
 *
 * The client is the **IDS client** — the same cookie jar that just completed the OAuth hop, because
 * that hop is what authorises these calls at all.
 *
 * Internal because it speaks in wire rows: [SchoolCardRecord] is a transport shape, and the rest of
 * the app is meant to see a [SchoolCardSnapshot] instead.
 */
internal class SchoolCardApi(
    private val client: HttpClient,
    private val json: Json,
) {

    /**
     * The card page the current session is being read from, used as the `Referer` of every call.
     *
     * Set by [SchoolCardSession] to the address its login chain ended on. A constant would be simpler
     * and is what a first attempt used, but the card system validates browser-shaped requests: the
     * `Referer` is supposed to be the page the client is *on*, and a session established through a
     * different address than the one being claimed is exactly the kind of mismatch such a check looks
     * for.
     */
    private var referer: String = SchoolCardEndpoints.OAUTH_CALLBACK

    /** Records where the login chain landed, so later calls claim the right origin. */
    fun onLandedAt(url: String) {
        referer = url
    }

    /**
     * The card handle, from the URL the OAuth chain ended on.
     *
     * The chain's last hop back into the card system carries the handle as `?openid=…`, while the
     * original's path — reading it from the rendered page — only works once the card system has drawn
     * its home page. The URL is checked first because it costs nothing and because a page that renders
     * an error still names the handle it was for. Both are needed: the reference behaviour is the
     * fallback, not the first choice.
     */
    fun extractOpenIdFromUrl(url: String): String? =
        runCatching { Url(url).parameters["openid"] }.getOrNull()?.takeIf { it.isNotEmpty() }

    /**
     * Reads the card handle out of the page the login chain ended on.
     *
     * The HTML is handed in rather than fetched here: that page is the response the IDS session already
     * holds, and asking for it a second time would be a second round trip — and a *second login*, since
     * walking the chain is what establishes the card session.
     *
     * The original looked for `id="openid"` **and** `type="hidden"`. Only the id is required here: a
     * page that names the field `openid` is the page, and demanding the attribute as well would turn a
     * cosmetic markup change into a hard failure.
     */
    fun extractOpenId(html: String): String? = parseOpenId(Ksoup.parse(html))

    /** The account page, whose balance [parseBalance] scrapes. */
    suspend fun fetchAccountPage(openId: String): String {
        val response = execute {
            client.get("${SchoolCardEndpoints.MY_ACCOUNT}?openid=$openId") { cardHeaders() }
        }
        val body = response.bodyAsText()
        // Logged on every read, not only on failure: whether the card system served the page or
        // redirected away from it is the difference between "the markup changed" and "the handle is not
        // valid", and the two need different fixes.
        appLog.i {
            "[SchoolCardApi] openMyAccount answered HTTP ${response.status.value}, " +
                    "location=${response.headers[HttpHeaders.Location] ?: "<none>"}, ${body.length} chars"
        }

        // With the AJAX headers this endpoint answers a refused handle as JSON at HTTP 200 — the same
        // shape the trade query uses — so the page is only a page when the endpoint said so.
        refusalMessage(body)?.let { throw SchoolCardProtocolException("校园卡账户查询被拒绝：$it") }
        return body
    }

    /**
     * The endpoint's own refusal, or null when the answer is not one.
     *
     * `{"success":false,"message":"openid无效，页面丢失!"}` at **HTTP 200** is how this system refuses a
     * handle, so a status code is not the verdict. Returning the body as a page instead would send it to
     * the scraper, which would find no amount and report a markup problem that does not exist.
     */
    private fun refusalMessage(body: String): String? {
        val root =
            runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        if ((root["success"] as? JsonPrimitive)?.booleanOrNull != false) return null
        return (root["message"] as? JsonPrimitive)?.contentOrNull.orEmpty().ifEmpty { "未知原因" }
    }

    /**
     * The two headers the card system's session manager requires before it will look at a request.
     *
     * Measured, not guessed. Without `X-Requested-With`, both `queryCardSelfTradeList` and
     * `openMyAccount` are answered `302 → /errorPage?message=资源受限，页面丢失!` — a redirect raised
     * *before* the controller runs, whatever the handle. With it, the same requests are answered as
     * JSON, so the controller's own verdict comes back.
     *
     * That distinction is the whole reason this was hard to see: the redirect carries a message about a
     * missing page, which reads as "your handle is dead", while the endpoint had not been asked yet.
     *
     * The reference app sets neither header, so this is a place where reproducing it exactly is what
     * breaks — the card system has moved on since it was written.
     */
    private fun HttpRequestBuilder.cardHeaders() {
        header("X-Requested-With", "XMLHttpRequest")
        header(HttpHeaders.Referrer, referer)
    }

    /**
     * Reads the day's transactions.
     *
     * The endpoint is a **POST** whose body carries the range and the `openid` as well as the query
     * string; the original sent both, so both are sent here.
     *
     * A missing `resultData` is an empty list rather than an error: a card that was not used on a day
     * the server has no rows for is a perfectly ordinary answer, and `resultData: []` and an absent
     * field mean the same thing to a reader.
     */
    suspend fun queryTradeList(openId: String, from: String, to: String): List<SchoolCardRecord> {
        val url = "${SchoolCardEndpoints.SELF_TRADE_LIST}?openid=$openId"
        appLog.i {
            "[SchoolCardApi] POST $url, handle=${handleShape(openId)}, body=" +
                    """{"beginDate":"$from","endDate":"$to","tradeType":"$TRADE_TYPE_ALL"}"""
        }
        val response = execute {
            client.post(url) {
                contentType(ContentType.Application.Json)
                cardHeaders()
                setBody(
                    TradeListBody(
                        beginDate = from,
                        endDate = to,
                        tradeType = TRADE_TYPE_ALL,
                        openid = openId,
                    ),
                )
            }
        }
        val text = response.bodyAsText()
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull()
        if (root !is JsonObject) {
            // The card system answers a request it will not serve with a redirect into its own error
            // page, and that page *names the reason* in its `message` parameter. "Non-JSON response"
            // alone says nothing, so the target is both logged and put in the exception.
            val location = response.headers[HttpHeaders.Location]
            appLog.w {
                "queryCardSelfTradeList answered HTTP ${response.status.value}" +
                        ", location=${location ?: "<none>"}" +
                        ", contentType=${response.headers[HttpHeaders.ContentType] ?: "<none>"}" +
                        ", body=${text.take(200)}"
            }
            throw SchoolCardProtocolException(
                "校园卡流水返回了非 JSON 响应 (HTTP ${response.status.value}" +
                        (location?.let { "，跳转到 $it" } ?: "") + ")",
            )
        }

        // The endpoint reports its own refusals in the body with HTTP 200, so a HTTP status is not the
        // verdict — this field is. A refused handle is reported as such rather than as an empty day,
        // which would otherwise read as "you spent nothing".
        val success = (root["success"] as? JsonPrimitive)?.booleanOrNull
        if (success == false) {
            val message = (root["message"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            throw SchoolCardProtocolException("校园卡流水查询被拒绝：$message")
        }

        val rows = root["resultData"] as? JsonArray ?: return emptyList()
        return rows.mapNotNull { (it as? JsonObject)?.let(::parseCardRecord) }
    }

    /**
     * A handle as much as may be logged: its length and a short prefix.
     *
     * A `openid` is a session credential, so it is not written to the log in full on a device whose logs
     * are routinely pasted into an issue; the prefix is enough to tell two runs apart.
     */
    private fun handleShape(handle: String): String =
        if (handle.isEmpty()) "<empty>" else "${handle.length} chars, ${handle.take(6)}…"

    private suspend fun fetchText(url: String): String =
        execute { client.get(url) }.bodyAsText()

    /** Runs a request, translating anything the engine threw into [SchoolCardNetworkException]. */
    private suspend fun execute(block: suspend () -> HttpResponse): HttpResponse = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        throw SchoolCardNetworkException("校园卡请求失败", error)
    }

    /**
     * The request body, spelled exactly as the original sent it: `beginDate`, `endDate`, `tradeType`
     * and `openid`.
     *
     * `tradeType` is the original's own `"-1"` — "every kind of trade", not just spending. kotlinx's
     * serialization omits a property equal to its default, so it is declared without one: a shorter
     * body is not the same request.
     */
    @Serializable
    private class TradeListBody(
        val beginDate: String,
        val endDate: String,
        val tradeType: String,
        val openid: String,
    )

    private companion object {
        const val TRADE_TYPE_ALL = "-1"
    }
}

/**
 * The card system's own session handle.
 *
 * The original walked the OAuth page's inputs looking for `id="openid"` **and** `type="hidden"`. Only
 * the id is required here: a page that names the field `openid` is the page, and demanding the
 * attribute as well would turn a cosmetic markup change into a hard failure.
 */
internal fun parseOpenId(document: Document): String? =
    document.getElementsByTag("input")
        .firstOrNull { it.id() == "openid" }
        ?.attr("value")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

/**
 * Reads the balance off the account page.
 *
 * The original reached it positionally — the first `<li>`'s second child's second child's inner HTML —
 * and answered a fixed sentinel string when it was not there, which the home card then rendered as a
 * balance. Positional navigation is kept as the primary reading, because that *is* the page layout
 * this was written against and a selector invented here could silently pick a different cell.
 *
 * Two departures, both about failing usefully rather than differently:
 *
 *  - a page that does not match falls back to the first number the page's own text carries, so a
 *    wrapper that stops being at that exact position is not fatal;
 *  - finding nothing is reported by returning null, instead of returning a string that reads like
 *    money. The caller needs to tell "the page had no balance" from "the balance is zero".
 */
internal fun parseBalance(html: String): String? {
    val document = Ksoup.parse(html)
    val positional = document.getElementsByTag("li").firstOrNull()
        ?.children()?.elementAtOrNull(1)
        ?.children()?.elementAtOrNull(1)
        ?.text()
        ?.trim()

    val candidate = positional?.takeIf { it.isNotEmpty() } ?: document.text()
    val match = AMOUNT.find(candidate) ?: run {
        appLog.w {
            "[SchoolCardApi] The account page carried no amount: ${
                document.text().take(256)
            }"
        }
        return null
    }
    return match.value.removePrefix("+")
}

/**
 * The first number on the page that looks like an amount.
 *
 * The fallback path reads the *whole* document rather than a guessed selector, so it cannot pick up a
 * different cell than the original read unless the page has been restructured around it — in which
 * case a number is still a better answer than none, and the log line says what the page held.
 */
private val AMOUNT = Regex("""-?\d+(?:\.\d+)?""")
