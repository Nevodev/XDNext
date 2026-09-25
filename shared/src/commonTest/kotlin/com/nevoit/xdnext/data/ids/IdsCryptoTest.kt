package com.nevoit.xdnext.data.ids

import com.nevoit.xdnext.core.crypto.AesCbc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Golden-vector tests for the two AES-CBC schemes the IDS protocol uses.
 *
 * These are the highest-value tests in the login slice. If the ciphertext is wrong the server does not
 * report an encryption error — it reports a wrong password, which is indistinguishable from a user
 * typo and would waste hours of debugging.
 *
 * The expected values were produced by **Node's `crypto` module** (`aes-128-cbc`, PKCS#7), an
 * implementation that shares no code with `cryptography-kotlin`, so agreement is real evidence rather
 * than a round-trip through the same bug.
 */
class IdsCryptoTest {

    private val crypto = IdsCrypto(AesCbc())

    @Test
    fun passwordEncryptionMatchesAnIndependentImplementation() {
        assertEquals(
            "C5sV2ktEoPUVHc/EwB811XdOjEzy1KOa9SoQa2jv+TRQqIFmdcZcioVrknG9szVQ8cQxKMCTq+ttBeGS21jlj6v63dO9qp015jwz9BlYkog=",
            crypto.encryptPassword(password = "hunter2", salt = "0123456789abcdef"),
            "Basic case: 7-byte password, no padding boundary involved.",
        )

        assertEquals(
            "mR8mhV85yt0euPwIHPXAPWYXqCypXjZ411y5tCqY1WyQx3uWUyWTdmroT4o3VxPZQU/LQ5vnE0LA5KmwkhN0tItQLUV8n8AfyUCrjz3CBa0=",
            crypto.encryptPassword(password = "P@ssw0rd!测试", salt = "a1b2c3d4e5f60718"),
            "Non-ASCII password: the UTF-8 encoding of the password must survive.",
        )

        assertEquals(
            "hXXR6JORed2oZR7uKPidfVct2z4nqTkDWOnLYaw1sBKZVk6iWS0M2+xs+KWlIREJuQioxNK+jvmC3ONO/bsONDEE71EYD6qdYPMT/m/KWdKJ1WeZhZWv2asPhI/HLzFrOZ4BWXov7zlgzzh7hf1dGZDrRNsm0+i1sLjvTNuBp6M=",
            crypto.encryptPassword(password = "x".repeat(48), salt = "ABCDEFGHIJKLMNOP"),
            "Block boundary: 64-byte prefix + 48 bytes is an exact multiple of 16, so PKCS#7 must " +
                    "append a whole extra block. Getting this wrong is the classic off-by-one-block bug.",
        )
    }

    @Test
    fun passwordEncryptionIsDeterministic() {
        // AES-CBC with the protocol's fixed IV and fixed key must be reproducible, otherwise the
        // server cannot verify it.
        val first = crypto.encryptPassword("hunter2", "0123456789abcdef")
        val second = crypto.encryptPassword("hunter2", "0123456789abcdef")
        assertEquals(first, second)
    }

    @Test
    fun captchaSignatureMatchesAnIndependentImplementation() {
        val noise =
            "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678" +
                    "ABCDEFGHJKMNPQRSTWXYZabcdefhijkm"
        assertEquals(80, noise.length, "fixture must be exactly the noise length the scheme uses")

        assertEquals(
            "D3dmHlJKZrzaF/AqeGE0w9haTHCzn4NkFSMQdYAERs0qUB234ulO1tkR0Y4VXNTS3s2zFaXDO+y64Wa2EhjDriNfvRXi4k62sG+o7lM2Sm+YSQ0SjxeNnCgNZesa5xgNm/xIfnJL3vLrDdL5OWq0iuZhT3MmXslO3WNPMuIlMt1LgV811O9QBHXKoOlnpGKV+z6nJDw4vQc70TxrFkn+pw==",
            crypto.signCaptchaPayloadWithNoise(
                payloadJson = CAPTCHA_PAYLOAD,
                key = "0123456789abcdef".encodeToByteArray(),
                noise = noise,
            ),
        )
    }

    @Test
    fun captchaSignatureVariesWithTheNoise() {
        val key = "0123456789abcdef".encodeToByteArray()
        val first = crypto.signCaptchaPayload(CAPTCHA_PAYLOAD, key)
        val second = crypto.signCaptchaPayload(CAPTCHA_PAYLOAD, key)
        // The random prefix and IV differ per call, so a repeated request is never byte-identical.
        assertTrue(first != second, "two signatures should differ because the noise is random")
    }

    @Test
    fun browserFingerprintIsThirtyTwoUppercaseHexCharacters() {
        val pattern = Regex("^[0-9A-F]{32}$")
        repeat(200) {
            val fingerprint = crypto.generateBrowserFingerprint()
            assertEquals(32, fingerprint.length)
            assertTrue(
                pattern.matches(fingerprint),
                "fingerprint '$fingerprint' must match the pattern the original validated against",
            )
        }
    }

    private companion object {
        const val CAPTCHA_PAYLOAD =
            """{"canvasLength":280,"moveLength":100,"tracks":[""" +
                    """{"a":0,"b":0,"c":0},{"a":100,"b":2,"c":420}]}"""
    }
}
