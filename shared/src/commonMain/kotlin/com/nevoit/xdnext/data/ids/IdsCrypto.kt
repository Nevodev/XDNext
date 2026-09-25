package com.nevoit.xdnext.data.ids

import com.nevoit.xdnext.core.crypto.AesCbc
import com.nevoit.xdnext.data.ids.IdsCrypto.Companion.CAPTCHA_ALPHABET
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

/**
 * The two independent AES-CBC schemes used by Xidian's IDS (统一认证服务).
 *
 * These are **not** interchangeable and the original Flutter code made that easy to get wrong: it
 * defined two different top-level functions that were both called `aesEncrypt`, in different files,
 * with different key derivation, IV handling and prefixing. They are named explicitly here.
 *
 * The schemes are reproduced exactly; any drift breaks every login, so both are covered by
 * fixed-vector unit tests in `IdsCryptoTest`.
 */
class IdsCrypto(private val aes: AesCbc) {

    /**
     * Encrypts an IDS account password for `POST /authserver/login`.
     *
     * Faithful to `IDSSession.aesEncrypt` in the original:
     *  - key   = the login page's `pwdEncryptSalt` value, taken as UTF-8 bytes (16 bytes in practice)
     *  - iv    = the ASCII literal `xidianscriptsxdu`
     *  - plain = `"xidianscriptsxdu"` repeated four times (64 bytes) followed by the password
     *  - padding = PKCS#7
     *
     * The original disabled the library's padding and hand-rolled PKCS#7 instead; the bytes produced
     * are identical to standard PKCS#7, so the padding is delegated to the provider here.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun encryptPassword(password: String, salt: String): String {
        val key = salt.encodeToByteArray()
        val plaintext = (PASSWORD_PREFIX + password).encodeToByteArray()
        val ciphertext = aes.encrypt(key = key, iv = IV.encodeToByteArray(), plaintext = plaintext)
        return Base64.encode(ciphertext)
    }

    /**
     * Signs a slider-captcha payload for `POST /authserver/common/verifySliderCaptcha.htl`.
     *
     * Faithful to the other `aesEncrypt` (the one in `network_client.dart`):
     *  - key   = the last 16 bytes of the captcha's `smallImage` (supplied by the caller)
     *  - iv    = 16 random characters drawn from [CAPTCHA_ALPHABET]
     *  - plain = 64 random characters from the same alphabet, followed by [payloadJson]
     *  - padding = PKCS#7
     *
     * The random prefix and IV are protocol theatre, not security: the server only needs the payload
     * to round-trip. They are nevertheless reproduced because the server may validate the shape.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun signCaptchaPayload(
        payloadJson: String,
        key: ByteArray,
        random: Random = Random.Default
    ): String =
        signCaptchaPayloadWithNoise(
            payloadJson = payloadJson,
            key = key,
            noise = randomAlphabetString(80, random),
        )

    /**
     * The body of [signCaptchaPayload] with the noise supplied explicitly.
     *
     * Split out so a test can pin the noise and compare the ciphertext against a vector produced by an
     * independent implementation. The noise is protocol theatre, so making it injectable costs nothing.
     */
    @OptIn(ExperimentalEncodingApi::class)
    internal fun signCaptchaPayloadWithNoise(
        payloadJson: String,
        key: ByteArray,
        noise: String,
    ): String {
        require(key.size == 16) { "slider captcha key must be 16 bytes but was ${key.size}" }
        require(noise.length == 80) { "captcha noise must be 80 characters but was ${noise.length}" }

        val plaintext = (noise.substring(0, 64) + payloadJson).encodeToByteArray()
        val iv = noise.substring(64, 80).encodeToByteArray()
        return Base64.encode(aes.encrypt(key = key, iv = iv, plaintext = plaintext))
    }

    /**
     * Produces the IDS "trusted device" fingerprint: 16 secure-random bytes as 32 uppercase hex chars.
     *
     * Despite the name in the original (`idsBrowserFingerprint`) this is not derived from the device
     * at all — it is a random identifier that is stored and replayed via `GET /authserver/bfp/info`.
     */
    fun generateBrowserFingerprint(random: Random = Random.Default): String =
        random.nextBytes(16).joinToString("") { byte ->
            val v = byte.toInt() and 0xFF
            HEX[v shr 4].toString() + HEX[v and 0x0F]
        }

    private fun randomAlphabetString(length: Int, random: Random): String =
        buildString(length) {
            repeat(length) { append(CAPTCHA_ALPHABET[random.nextInt(CAPTCHA_ALPHABET.length)]) }
        }

    companion object {
        /**
         * `"xidianscriptsxdu"` repeated four times: the 64-byte prefix the IDS login form expects.
         * This comes from `libxduauth`, which the original credits.
         */
        internal const val PASSWORD_PREFIX =
            "xidianscriptsxduxidianscriptsxduxidianscriptsxduxidianscriptsxdu"

        /** The fixed AES-CBC IV used for password encryption. */
        internal const val IV = "xidianscriptsxdu"

        /** Alphabet used for the slider-captcha noise. Ambiguous glyphs are deliberately absent. */
        internal const val CAPTCHA_ALPHABET =
            "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"

        private const val HEX = "0123456789ABCDEF"
    }
}
