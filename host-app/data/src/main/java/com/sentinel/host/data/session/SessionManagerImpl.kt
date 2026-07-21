package com.sentinel.host.data.session

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.sentinel.host.domain.session.SessionManager

/**
 * JWT and session persistence via EncryptedSharedPreferences.
 * Single source of truth for credentials.
 */
class SessionManagerImpl(context: Context) : SessionManager {

    companion object {
        private const val PREFS_NAME = "sentinel_session"
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_SERVER_URL = "server_url"
    }

    private val prefs: SharedPreferences by lazy {
        val deviceContext = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }

        EncryptedSharedPreferences.create(
            PREFS_NAME,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            deviceContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    override fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    override fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    override fun saveServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
    }

    override fun getServerUrl(): String? {
        return prefs.getString(KEY_SERVER_URL, null)
    }

    override fun hasSession(): Boolean {
        return getToken() != null && getServerUrl() != null
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}
