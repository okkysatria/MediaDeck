package com.mediadeck.app.util.smb

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmbCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "smb_credentials",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun getPassword(): String = preferences.getString(PASSWORD_KEY, "").orEmpty()

    fun savePassword(password: String) {
        preferences.edit().putString(PASSWORD_KEY, password).commit()
    }

    private companion object {
        const val PASSWORD_KEY = "password"
    }
}
