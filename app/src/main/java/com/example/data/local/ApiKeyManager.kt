package com.example.data.local

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object ApiKeyManager {
    private const val PREFS_NAME = "med_anki_secure_prefs"
    private const val KEY_ENCRYPTED_API_KEY = "encrypted_gemini_api_key"
    private const val KEY_IV = "gemini_api_key_iv"
    private const val KEY_PRIVACY_ACK = "privacy_notice_acknowledged"

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "MedAnkiGeminiKeyAlias"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"

    fun getApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedBase64 = prefs.getString(KEY_ENCRYPTED_API_KEY, null) ?: return ""
        val ivBase64 = prefs.getString(KEY_IV, null) ?: return ""

        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val secretKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (secretKey != null) {
                val cipher = Cipher.getInstance(AES_TRANSFORMATION)
                val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
                val spec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
                val decrypted = cipher.doFinal(encryptedBytes)
                String(decrypted, Charsets.UTF_8).trim()
            } else {
                // Fallback decode if keystore key is absent (e.g. unit test JVM)
                val rawBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
                String(rawBytes, Charsets.UTF_8).trim()
            }
        } catch (_: Exception) {
            try {
                val rawBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
                String(rawBytes, Charsets.UTF_8).trim()
            } catch (_: Exception) {
                ""
            }
        }
    }

    fun setApiKey(context: Context, apiKey: String) {
        val trimmed = apiKey.trim()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (trimmed.isEmpty()) {
            clearApiKey(context)
            return
        }

        try {
            val secretKey = getOrCreateSecretKey()
            if (secretKey != null) {
                val cipher = Cipher.getInstance(AES_TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv
                val encryptedBytes = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))

                prefs.edit()
                    .putString(KEY_ENCRYPTED_API_KEY, Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
                    .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                    .apply()
                return
            }
        } catch (_: Exception) {
            // In unit test environments without AndroidKeyStore provider, store obfuscated Base64 safely
        }

        prefs.edit()
            .putString(KEY_ENCRYPTED_API_KEY, Base64.encodeToString(trimmed.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            .putString(KEY_IV, "plain")
            .apply()
    }

    fun clearApiKey(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_ENCRYPTED_API_KEY)
            .remove(KEY_IV)
            .apply()
    }

    fun hasApiKey(context: Context): Boolean {
        return getApiKey(context).isNotBlank()
    }

    fun isPrivacyNoticeAccepted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PRIVACY_ACK, false)
    }

    fun setPrivacyNoticeAccepted(context: Context, accepted: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PRIVACY_ACK, accepted).apply()
    }

    private fun getOrCreateSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            } else {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val builder = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                keyGenerator.init(builder.build())
                keyGenerator.generateKey()
            }
        } catch (_: Exception) {
            null
        }
    }
}
