package com.sentinel.admin.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.sentinel.admin.domain.repository.AuthRepository

/**
 * Encrypted token storage for Admin authentication.
 *
 * Uses EncryptedSharedPreferences backed by Android Keystore.
 * Token persists across app restarts. Cleared on explicit logout.
 *
 * @param context Application context for creating EncryptedSharedPreferences.
 */
class AuthRepositoryImpl(context: Context) : AuthRepository {

    companion object {
        private const val PREFS_NAME = "sentinel_admin_auth"
        private const val KEY_TOKEN = "jwt_token"
    }

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    override fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    override fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }
}
