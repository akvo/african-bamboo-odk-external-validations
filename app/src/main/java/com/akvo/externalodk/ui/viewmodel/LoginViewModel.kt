package com.akvo.externalodk.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val serverUrl: String = "https://eu.kobotoolbox.org",
    val formId: String = ""
) {
    val isFormValid: Boolean
        get() = username.isNotBlank() &&
                password.isNotBlank() &&
                serverUrl.isNotBlank() &&
                formId.isNotBlank()
}

@HiltViewModel
class LoginViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(username = value) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun onServerUrlChange(value: String) {
        _uiState.update { it.copy(serverUrl = value) }
    }

    fun onFormIdChange(value: String) {
        _uiState.update { it.copy(formId = value) }
    }

    fun startLoginAndDownloadProcess() {
        // TODO: Will be implemented with API sync
        // For now, the navigation is handled by the screen's onDownloadStart callback
    }
}
