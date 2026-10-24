package com.mediadeck.app.util.smb

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmbCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(SECURE_PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getPassword(): String {
        val encryptedPassword = preferences.getString(PASSWORD_KEY, null)
        if (encryptedPassword != null) {
            return try {
                decrypt(encryptedPassword)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decrypt SMB password", e)
                ""
            }
        }

        val legacyPreferences = openLegacyPreferences() ?: return ""
        if (!legacyPreferences.contains(PASSWORD_KEY)) return ""

        return try {
            val password = legacyPreferences.getString(PASSWORD_KEY, "").orEmpty()
            preferences.edit { putString(PASSWORD_KEY, encrypt(password)) }
            password
        } catch (e: Exception) {
            Log.e(TAG, "Failed to migrate SMB password", e)
            ""
        }
    }

    @Synchronized
    fun savePassword(password: String) {
        preferences.edit { putString(PASSWORD_KEY, encrypt(password)) }
    }

    @Suppress("DEPRECATION")
    private fun openLegacyPreferences(): SharedPreferences? = try {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            LEGACY_PREFERENCES_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        Log.w(TAG, "Could not open legacy SMB credential storage", e)
        null
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val payload = ByteBuffer.allocate(cipher.iv.size + ciphertext.size)
            .put(cipher.iv)
            .put(ciphertext)
            .array()
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encodedValue: String): String {
        val payload = Base64.decode(encodedValue, Base64.NO_WRAP)
        require(payload.size > GCM_IV_LENGTH_BYTES) { "Invalid encrypted SMB password" }
        val buffer = ByteBuffer.wrap(payload)
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        buffer.get(iv)
        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private companion object {
        const val TAG = "SmbCredentialStore"
        const val PASSWORD_KEY = "password"
        const val LEGACY_PREFERENCES_NAME = "smb_credentials"
        const val SECURE_PREFERENCES_NAME = "smb_credentials_secure"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "mediadeck_smb_credentials_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
