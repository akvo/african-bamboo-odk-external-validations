package org.akvo.afribamodkvalidator.data.session

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sharedPreferences: SharedPreferences = createEncryptedPrefs()

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

    private fun createEncryptedPrefs(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create encrypted prefs, clearing corrupted data", e)
            clearCorruptedPrefs()
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    private fun clearCorruptedPrefs() {
        val sharedPrefsDir = File(context.filesDir.parent, "shared_prefs")

        // Delete all related SharedPreferences files
        sharedPrefsDir.listFiles()?.forEach { file ->
            if (file.name.contains(PREFS_NAME) ||
                file.name.contains("__androidx_security_crypto")
            ) {
                file.delete()
                Log.d(TAG, "Deleted file: ${file.name}")
            }
        }

        // Clear all encryption keys from Android Keystore
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)

            // Delete the master key
            if (keyStore.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                Log.d(TAG, "Deleted master key: ${MasterKey.DEFAULT_MASTER_KEY_ALIAS}")
            }

            // Also try the alternative alias format
            val altAlias = "_androidx_security_master_key_"
            if (keyStore.containsAlias(altAlias)) {
                keyStore.deleteEntry(altAlias)
                Log.d(TAG, "Deleted alt master key: $altAlias")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete master key", e)
        }
    }

    companion object {
        private const val TAG = "SessionManager"
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
