package com.nevoit.xdnext.data.energy

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
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** The `timestamp`/`signature` pair the energy system demands on every business call. */
data class EnergySignature(val timestamp: String, val signature: String)

/** One row of `H5QueryMeterList`; only the fields this app reads are modelled. */
internal data class EnergyMeterRow(
    val mediumCode: String?,
    val lastNum: Double?,
    val metId: String?,
    val lastReadDate: LocalDate?,
)

/** The energy system answered in a way the protocol does not define. */
class EnergyProtocolException(message: String) : Exception(message)

/**
 * The energy system could not be reached, or the exchange was cut short.
 *
 * Transport failures are translated into this type here rather than surfacing as whatever the engine
 * threw. That keeps the cache hint ([electricityCacheHintFor]) and the screens free of a dependency
 * on the HTTP engine's exception hierarchy — which differs per platform and, for "no campus network",
 * is a plain `IOException` that nothing would otherwise recognise.
 */
class EnergyNetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The HTTP surface of the energy management system.
 *
 * Ported from `EnergySession._request` / `_requestNewEnergyInfo` in the original, and split the same
 * way [com.nevoit.xdnext.data.ids.IdsAuthApi] is split from its session: this class performs requests
 * and nothing else, while [EnergySession] owns the flow, the cache and the fallback policy.
 *
 * The client is the *IDS* client — the same cookie jar that just completed the OAuth hop, because
 * that hop is what makes these calls authorised at all.
 *
 * Every business call repeats the original's shape exactly: mint a signature pair, encrypt the JSON
 * payload, and send it as `content` — in the query string for reads, inside a JSON envelope for
 * writes. [getSignature] itself is the one exception: it carries no signature and no envelope.
 */
class EnergyApi(
    private val client: HttpClient,
    private val crypto: EnergyCrypto,
    private val json: Json,
) {

    /**
     * Mints the per-request signature pair.
     *
     * The original asked for a fresh pair before **every** call rather than reusing one, so this is
     * called per request and never cached.
     */
    suspend fun getSignature(): EnergySignature {
        val response = execute(EnergyEndpoints.GET_SIGNATURE) {
            client.post(it) {
                contentType(ContentType.Application.Json)
                setBody(
                    GetSignatureBody(
                        data = "",
                        accessToken = "",
                        opCode = EnergyEndpoints.OP_CODE,
                        requestId = "",
                    ),
                )
            }
        }
        val data = response.asJson().field("data").asObject("data")
        return EnergySignature(
            timestamp = data.field("timestamp").asText("data.timestamp"),
            signature = data.field("signature").asText("data.signature"),
        )
    }

    /**
     * Exchanges the CAS-issued `code` for an energy-system identity.
     *
     * The response is deliberately discarded: what matters is the session cookie it sets, which every
     * later call depends on.
     */
    suspend fun oauthGetUserInfo(code: String) {
        read(EnergyEndpoints.OAUTH_GET_USER_INFO, OauthBody(code))
    }

    /** Signs in as the IDS account and returns the `NodeID` that owns the meters. */
    suspend fun h5UserIdLogin(userId: String): String {
        val root = write(
            EnergyEndpoints.H5_USER_ID_LOGIN,
            // `Pwd` is intentionally empty and `IsCehckPwd` is the original's own spelling. The IDS
            // session did the authenticating; this call only tells the energy system who it is.
            UserIdLoginBody(userId = userId, password = "", checkPassword = 1, nodeId = ""),
        )
        val first = root.field("ResData").asArray("ResData").firstOrNull()
            ?: throw EnergyProtocolException("H5UserIDLogIn 的 ResData 为空")
        return first.asObject("ResData[0]").field("NodeID").asText("ResData[0].NodeID")
    }

    /** Lists the meters under [nodeId]. */
    internal suspend fun queryMeterList(nodeId: String): List<EnergyMeterRow> {
        val root = read(EnergyEndpoints.H5_QUERY_METER_LIST, NodeBody(nodeId))
        return root.field("ResData").asObject("ResData").field("rows")
            .asArray("ResData.rows")
            .map { row ->
                val item = row.asObject("ResData.rows[]")
                EnergyMeterRow(
                    mediumCode = item.textOrNull("MediumCode"),
                    lastNum = item.doubleOrNull("LastNum"),
                    metId = item.textOrNull("MetID"),
                    lastReadDate = item.textOrNull("LastReadDate")?.let(::parseWireDate),
                )
            }
    }

    /**
     * Reads one meter from [from] to [to], both inclusive.
     *
     * A row that cannot be read is skipped rather than failing the whole query, but a response whose
     * rows are *all* unusable is reported: that pattern means the response shape changed, and
     * returning an empty list would silently turn into an empty chart.
     */
    suspend fun getMeterRead(metId: String, from: LocalDate, to: LocalDate): List<MeterInfo> {
        val root = read(
            EnergyEndpoints.GET_METER_READ,
            MeterReadBody(
                metId = metId,
                readTimeStart = from.toString(),
                readTimeEnd = to.toString(),
                readNum = "",
            ),
        )
        val rows = root.field("ResData").asObject("ResData").field("rows")
            .asArray("ResData.rows")
        val parsed = rows.mapNotNull { parseMeterInfo(it.asObject("ResData.rows[]")) }
        if (parsed.isEmpty() && rows.isNotEmpty()) {
            appLog.w { "GetMetRead returned ${rows.size} rows, none of which matched MeterInfo" }
            throw EnergyProtocolException("GetMetRead 返回了 ${rows.size} 行读数，但没有一行能被解析")
        }
        return parsed
    }

    // ---------------------------------------------------------------------------------------------
    // Transport
    // ---------------------------------------------------------------------------------------------

    /** A signed GET: the payload travels as an encrypted `content` query parameter. */
    private suspend inline fun <reified T> read(url: String, body: T): JsonObject {
        val signature = getSignature()
        // Two encoding passes, on purpose — see `percentEncodeComponent`. Dio adds the second one
        // itself, so this port has to hand Ktor a value that already carries the first.
        val content = percentEncodeComponent(encrypt(body))
        val response = execute(url) { target ->
            client.get(target) {
                url { parameters.append("content", content) }
                signatureHeaders(signature)
            }
        }
        return response.asJson()
    }

    /** A signed POST: the payload travels as an encrypted `content` field of a JSON envelope. */
    private suspend inline fun <reified T> write(url: String, body: T): JsonObject {
        val signature = getSignature()
        val envelope = ContentBody(encrypt(body))
        val response = execute(url) { target ->
            client.post(target) {
                contentType(ContentType.Application.Json)
                setBody(envelope)
                signatureHeaders(signature)
            }
        }
        return response.asJson()
    }

    private inline fun <reified T> encrypt(body: T): String =
        crypto.encryptContent(json.encodeToString(body))

    /**
     * Runs a request, translating anything the engine threw into [EnergyNetworkException].
     *
     * Serialisation and the request build happen inside the block, so a programming error there would
     * also arrive as a network failure; nothing in this class can distinguish them, and the log
     * carries the cause.
     */
    private suspend fun execute(
        url: String,
        block: suspend (String) -> HttpResponse,
    ): HttpResponse = try {
        block(url)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        throw EnergyNetworkException("${url.substringAfterLast('/')} 请求失败", error)
    }

    /**
     * Adds the five headers the original sent on every business call — including the two empty ones.
     * Whatever the server does with `OrgId`/`RequestID`, it is not this client's business to
     * optimise them away.
     */
    private fun HttpRequestBuilder.signatureHeaders(signature: EnergySignature) {
        header("timestamp", signature.timestamp)
        header("signature", signature.signature)
        header("OpCode", EnergyEndpoints.OP_CODE)
        header("OrgId", "")
        header("RequestID", "")
    }

    private suspend fun HttpResponse.asJson(): JsonObject {
        val endpoint = call.request.url.encodedPath.substringAfterLast('/')
        val text = bodyAsText()
        val element = runCatching { json.parseToJsonElement(text) }.getOrNull()
        if (element !is JsonObject) {
            appLog.w { "$endpoint answered with a non-JSON body: ${text.take(256)}" }
            throw EnergyProtocolException("$endpoint 返回了非 JSON 响应 (HTTP ${status.value})")
        }
        return element
    }

    // ---------------------------------------------------------------------------------------------
    // Response shape
    // ---------------------------------------------------------------------------------------------

    private fun JsonObject.field(name: String): JsonElement =
        this[name] ?: run {
            appLog.w { "Energy response is missing `$name`: ${toString().take(256)}" }
            throw EnergyProtocolException("用电系统响应缺少字段 `$name`")
        }

    private fun JsonElement.asObject(where: String): JsonObject =
        this as? JsonObject ?: throw EnergyProtocolException("用电系统响应的 `$where` 不是对象")

    private fun JsonElement.asArray(where: String): JsonArray =
        this as? JsonArray ?: throw EnergyProtocolException("用电系统响应的 `$where` 不是数组")

    private fun JsonElement.asText(where: String): String =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
            ?: throw EnergyProtocolException("用电系统响应的 `$where` 不是标量")

    private fun JsonObject.textOrNull(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    /**
     * Reads a number the server may or may not quote.
     *
     * `JsonPrimitive.doubleOrNull` parses the primitive's *content*, so `"12.5"` and `12.5` both
     * arrive here as 12.5 — which is exactly why the original needed `num.parse(...toString())` for
     * the meter list's `LastNum`.
     */
    private fun JsonObject.doubleOrNull(name: String): Double? =
        (this[name] as? JsonPrimitive)?.doubleOrNull

    // ---------------------------------------------------------------------------------------------
    // Request bodies
    //
    // Declared without defaults on purpose: kotlinx.serialization omits a property equal to its
    // default, and the server rejects a body with a missing field even when that field is unused.
    // The original posted every one of them literally.
    // ---------------------------------------------------------------------------------------------

    @Serializable
    private data class GetSignatureBody(
        val data: String,
        @SerialName("access_token") val accessToken: String,
        @SerialName("OpCode") val opCode: String,
        @SerialName("RequestID") val requestId: String,
    )

    @Serializable
    private data class ContentBody(val content: String)

    @Serializable
    private data class OauthBody(@SerialName("CODE") val code: String)

    @Serializable
    private data class UserIdLoginBody(
        @SerialName("UserID") val userId: String,
        @SerialName("Pwd") val password: String,
        @SerialName("IsCehckPwd") val checkPassword: Int,
        @SerialName("NodeID") val nodeId: String,
    )

    @Serializable
    private data class NodeBody(@SerialName("NodeID") val nodeId: String)

    @Serializable
    private data class MeterReadBody(
        @SerialName("MetID") val metId: String,
        @SerialName("ReadTimeS") val readTimeStart: String,
        @SerialName("ReadTimeE") val readTimeEnd: String,
        @SerialName("ReadNum") val readNum: String,
    )
}

/**
 * Parses one `GetMetRead` row, or null when it cannot be read.
 *
 * `ReadTime` is whatever the server sends for a reading timestamp; the original handed it to
 * `DateTime.parse`, which accepts an ISO date with or without a time part, so both are accepted here
 * and the time — midnight in every response seen — is dropped.
 */
internal fun parseMeterInfo(row: JsonObject): MeterInfo? {
    val readTime = (row["ReadTime"] as? JsonPrimitive)
        ?.takeIf { it !is JsonNull }
        ?.content
        ?.let(::parseWireDate)
        ?: return null
    val number = { name: String -> (row[name] as? JsonPrimitive)?.doubleOrNull }
    return MeterInfo(
        readTime = readTime,
        readNum = number("ReadNum") ?: return null,
        startNum = number("StartNum") ?: return null,
        endNum = number("EndNum") ?: return null,
    )
}

/** Reads an ISO-8601 date, ignoring any time part. */
internal fun parseWireDate(raw: String): LocalDate? {
    val datePart = raw.trim().substringBefore('T').substringBefore(' ')
    if (datePart.isEmpty()) return null
    return runCatching { LocalDate.parse(datePart) }.getOrNull()
}
