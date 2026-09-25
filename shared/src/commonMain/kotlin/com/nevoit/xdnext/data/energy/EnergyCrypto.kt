package com.nevoit.xdnext.data.energy

import com.nevoit.xdnext.core.crypto.AesCbc
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Encrypts the `content` parameter of every energy-system business call.
 *
 * Ported from the `Encrypter` block in `EnergySession._request`:
 *  - key = `1234567812345678` as UTF-8 (AES-128)
 *  - iv  = the same bytes
 *  - mode = CBC, padding = PKCS#7 (the library default, which the original did not override)
 *  - output = standard, padded Base64
 *
 * This is a **third** AES scheme, distinct from both IDS ones: neither the password scheme
 * ([com.nevoit.xdnext.data.ids.IdsCrypto.encryptPassword]) nor the captcha scheme shares its key
 * derivation. Getting the padding wrong here produces an opaque server-side failure, so the scheme is
 * covered by a fixed vector computed with Node's `crypto` — see `tools/README.md`.
 */
class EnergyCrypto(private val aes: AesCbc) {

    @OptIn(ExperimentalEncodingApi::class)
    fun encryptContent(plaintext: String): String {
        val ciphertext = aes.encrypt(
            key = EnergyEndpoints.CONTENT_KEY.encodeToByteArray(),
            iv = EnergyEndpoints.CONTENT_IV.encodeToByteArray(),
            plaintext = plaintext.encodeToByteArray(),
        )
        return Base64.encode(ciphertext)
    }
}

/**
 * Percent-encodes a value the way Dart's `Uri.encodeComponent` does: everything except
 * `A-Z a-z 0-9 - _ . ! ~ * ' ( )` becomes uppercase `%XX` over UTF-8 bytes.
 *
 * **The `content` parameter is encoded twice on the wire, and that is deliberate.** The original
 * applies `Uri.encodeComponent` to the Base64 itself and then hands it to dio, and dio encodes every
 * query parameter value again (`encodeMap(..., isQuery = true)` → `Uri.encodeQueryComponent`, which
 * dio has done since 5.0.2). Measured through the reference's own dependency lock — dio 5.11.1 — with
 * `reference/probe/energy_encoding_probe.dart`, the request line is:
 *
 * ```
 * GET /estManage/api/WeChat/V2/GetMetRead?content=NzfL...mcFMc2fij8aw...%252FuhbF2a8o...%252BBg
 * ```
 *
 * So the server un-escapes twice, and a single pass would send it Base64 it cannot read. Ktor encodes
 * the parameters a caller appends, so [percentEncodeComponent] supplies the first pass and Ktor's own
 * encoder the second — for this alphabet the second pass only turns `%` into `%25`, which is exactly
 * what dio produces.
 *
 * If the live server ever turns out to want one pass, this call is the single place to remove; the
 * request-shape test in `EnergyApiTest` pins the current bytes either way.
 */
internal fun percentEncodeComponent(value: String): String {
    val bytes = value.encodeToByteArray()
    val builder = StringBuilder(bytes.size)
    for (byte in bytes) {
        val code = byte.toInt() and 0xFF
        val char = code.toChar()
        if (char in UNRESERVED) {
            builder.append(char)
        } else {
            builder.append('%')
            builder.append(HEX[code shr 4])
            builder.append(HEX[code and 0x0F])
        }
    }
    return builder.toString()
}

private const val UNRESERVED =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'()"

private const val HEX = "0123456789ABCDEF"
