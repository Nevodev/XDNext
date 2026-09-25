package com.nevoit.xdnext.data.ids

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Wire shape of the captcha answer. Key names are single letters because the server expects them. */
@Serializable
internal data class CaptchaPayload(
    val canvasLength: Int,
    val moveLength: Int,
    val tracks: List<CaptchaTrack>,
)

@Serializable
internal data class CaptchaTrack(val a: Int, val b: Int, val c: Int)

/**
 * Serialises a drag into the payload the server decrypts.
 *
 * A top-level function rather than a method so the wire shape can be pinned by a test without building
 * an HTTP client.
 *
 * `moveLength` is the final horizontal position, not the track count. The original used the last point's
 * `a` value, and a mismatch here is rejected without explanation.
 */
internal fun buildCaptchaPayload(
    tracks: List<CaptchaTrackPoint>,
    canvasLength: Int,
    json: Json = Json,
): String = json.encodeToString(
    CaptchaPayload(
        canvasLength = canvasLength,
        moveLength = tracks.lastOrNull()?.x ?: 0,
        tracks = tracks.map { CaptchaTrack(a = it.x, b = it.y, c = it.millis) },
    ),
)

/**
 * Turns a recorded drag into a signed captcha answer.
 *
 * Split out from both the solver and the HTTP layer because three different callers need it — the
 * automatic solver, the manual UI, and tests.
 */
class SliderCaptchaVerifier(
    private val api: IdsAuthApi,
    private val idsCrypto: IdsCrypto,
    private val json: Json = Json,
) {

    internal fun buildPayload(tracks: List<CaptchaTrackPoint>, canvasLength: Int): String =
        buildCaptchaPayload(tracks, canvasLength, json)

    /** Signs [tracks] with the challenge's key and submits them. Returns true when accepted. */
    suspend fun verify(
        challenge: SliderCaptchaChallenge,
        tracks: List<CaptchaTrackPoint>,
    ): Boolean {
        if (tracks.isEmpty()) return false
        val payload = buildPayload(tracks, challenge.canvasWidth)
        val signature = idsCrypto.signCaptchaPayload(payload, challenge.pieceKey)
        return api.submitSliderCaptcha(signature)
    }
}
