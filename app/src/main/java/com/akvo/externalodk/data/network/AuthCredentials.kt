package com.akvo.externalodk.data.network

import com.akvo.externalodk.data.session.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthCredentials @Inject constructor(
    private val sessionManager: SessionManager
) {
    @Volatile
    var username: String = ""
        private set

    @Volatile
    var password: String = ""
        private set

    @Volatile
    var assetUid: String = ""
        private set

    @Volatile
    var serverUrl: String = ""
        private set

    val isSet: Boolean
        get() = username.isNotEmpty() && password.isNotEmpty()

    init {
        loadFromSession()
    }

    private fun loadFromSession() {
        sessionManager.getSession()?.let { session ->
            this.username = session.username
            this.password = session.password
            this.assetUid = session.assetUid
            this.serverUrl = normalizeServerUrl(session.serverUrl)
        }
    }

    fun set(username: String, password: String, assetUid: String, serverUrl: String) {
        val normalizedUrl = normalizeServerUrl(serverUrl)
        this.username = username
        this.password = password
        this.assetUid = assetUid
        this.serverUrl = normalizedUrl

        sessionManager.saveSession(
            username = username,
            password = password,
            serverUrl = normalizedUrl,
            assetUid = assetUid
        )
    }

    fun clear() {
        this.username = ""
        this.password = ""
        this.assetUid = ""
        this.serverUrl = ""
        sessionManager.clearSession()
    }

    private fun normalizeServerUrl(url: String): String {
        return if (url.endsWith("/")) url else "$url/"
    }
}
