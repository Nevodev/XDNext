package com.nevoit.xdnext.core.crypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES

/**
 * AES-CBC with PKCS#7 padding, backed by `cryptography-kotlin`.
 *
 * On Android this resolves to the JDK/JCA provider (AES/CBC/PKCS5Padding); the same code path is
 * intended to serve Apple targets later via the provider mechanism, which is why no platform code
 * appears here.
 *
 * The Xidian IDS protocol uses two independent AES-CBC schemes and they are **not** interchangeable
 * (see [com.nevoit.xdnext.data.ids.IdsCrypto]). Both are plain AES-CBC/PKCS#7 with a caller-supplied
 * 16-byte key and a caller-supplied 16-byte IV, so one primitive covers both.
 */
class AesCbc(private val provider: CryptographyProvider = CryptographyProvider.Default) {

    private val aes = provider.get(AES.CBC)

    /**
     * Encrypts [plaintext] with [key] and [iv].
     *
     * @param pad when true PKCS#7 padding is applied by the provider; when false the caller must
     *   supply a plaintext whose length is already a multiple of the 16-byte block size.
     */
    @OptIn(DelicateCryptographyApi::class)
    fun encrypt(
        key: ByteArray,
        iv: ByteArray,
        plaintext: ByteArray,
        pad: Boolean = true,
    ): ByteArray {
        require(key.size == 16 || key.size == 24 || key.size == 32) {
            "AES key must be 16, 24 or 32 bytes but was ${key.size}"
        }
        require(iv.size == 16) { "AES-CBC IV must be 16 bytes but was ${iv.size}" }
        val decoded = aes.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
        return decoded.cipher(pad).encryptWithIvBlocking(iv, plaintext)
    }

    @OptIn(DelicateCryptographyApi::class)
    fun decrypt(
        key: ByteArray,
        iv: ByteArray,
        ciphertext: ByteArray,
        pad: Boolean = true,
    ): ByteArray {
        require(iv.size == 16) { "AES-CBC IV must be 16 bytes but was ${iv.size}" }
        val decoded = aes.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
        return decoded.cipher(pad).decryptWithIvBlocking(iv, ciphertext)
    }
}
