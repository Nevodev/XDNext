package com.nevoit.xdnext.core.store

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import com.nevoit.xdnext.core.log.appLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidSecureStore(context: Context) : SecureStore {

    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun putString(key: String, value: String) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))

        val packed = ByteArray(iv.size + ciphertext.size)
        iv.copyInto(packed, 0)
        ciphertext.copyInto(packed, iv.size)

        preferences.edit {
            putString(key, Base64.getEncoder().encodeToString(packed))
        }
    }

    override suspend fun getString(key: String): String? = withContext(Dispatchers.IO) {
        val stored = preferences.getString(key, null) ?: return@withContext null

        try {
            val packed = Base64.getDecoder().decode(stored)
            if (packed.size <= IV_LENGTH_BYTES) {
                appLog.w { "Dropping truncated secret for key $key" }
                preferences.edit { remove(key) }
                return@withContext null
            }

            val iv = packed.copyOfRange(0, IV_LENGTH_BYTES)
            val ciphertext = packed.copyOfRange(IV_LENGTH_BYTES, packed.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (error: GeneralSecurityException) {
            // Most often an invalidated Keystore key. Treat as absent so the user can log in again.
            appLog.w(error) { "Could not decrypt secret for key $key; discarding it" }
            preferences.edit { remove(key) }
            null
        } catch (error: IllegalArgumentException) {
            appLog.w(error) { "Secret for key $key was not valid base64; discarding it" }
            preferences.edit { remove(key) }
            null
        }
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        preferences.edit { remove(key) }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        preferences.edit { clear() }
    }

    private fun masterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // A fresh IV per encryption is mandatory for GCM; the Keystore enforces it for us.
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "xdnext_secure"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "xdnext_secure_master"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
        const val IV_LENGTH_BYTES = 12
    }
}
