package org.akvo.afribamodkvalidator.data.session

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveSession(
        username: String,
        password: String,
        serverUrl: String,
        assetUid: String
    ) {
        sharedPreferences.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .putString(KEY_SERVER_URL, serverUrl)
            .putString(KEY_ASSET_UID, assetUid)
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .apply()
    }

    fun getSession(): SessionData? {
        val isLoggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
        if (!isLoggedIn) return null

        val username = sharedPreferences.getString(KEY_USERNAME, null) ?: return null
        val password = sharedPreferences.getString(KEY_PASSWORD, null) ?: return null
        val serverUrl = sharedPreferences.getString(KEY_SERVER_URL, null) ?: return null
        val assetUid = sharedPreferences.getString(KEY_ASSET_UID, null) ?: return null

        return SessionData(
            username = username,
            password = password,
            serverUrl = serverUrl,
            assetUid = assetUid
        )
    }

    fun isLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun clearSession() {
        sharedPreferences.edit()
            .clear()
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "external_odk_session"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_ASSET_UID = "asset_uid"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }
}

data class SessionData(
    val username: String,
    val password: String,
    val serverUrl: String,
    val assetUid: String
)
