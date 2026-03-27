package org.akvo.afribamodkvalidator.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
import org.akvo.afribamodkvalidator.data.dto.KoboAsset
import org.akvo.afribamodkvalidator.data.network.AuthCredentials
import org.akvo.afribamodkvalidator.data.network.KoboApiService
import javax.inject.Inject

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val serverUrl: String = "https://kc-eu.kobotoolbox.org",
    val assets: List<KoboAsset> = emptyList(),
    val selectedAsset: KoboAsset? = null,
    val isLoadingAssets: Boolean = false,
    val assetsError: String? = null
) {
    val areCredentialsValid: Boolean
        get() = username.isNotBlank() &&
                password.isNotBlank() &&
                serverUrl.isNotBlank()

    val isFormValid: Boolean
        get() = areCredentialsValid && selectedAsset != null

    val hasAssets: Boolean
        get() = assets.isNotEmpty()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authCredentials: AuthCredentials,
    private val apiService: KoboApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(username = value.trim()) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value.trim()) }
    }

    fun onServerUrlChange(value: String) {
        _uiState.update { it.copy(serverUrl = value.trim()) }
    }

    fun onAssetSelected(asset: KoboAsset) {
        _uiState.update { it.copy(selectedAsset = asset) }
    }

    fun fetchAssets() {
        val state = _uiState.value
        authCredentials.setTemporary(
            username = state.username.trim(),
            password = state.password,
            serverUrl = state.serverUrl.trim()
        )

        _uiState.update { it.copy(isLoadingAssets = true, assetsError = null) }

        viewModelScope.launch {
            try {
                val allAssets = mutableListOf<KoboAsset>()
                var start = 0
                val pageSize = KoboApiService.DEFAULT_PAGE_SIZE

                do {
                    val response = apiService.getAssets(limit = pageSize, start = start)
                    allAssets.addAll(response.results.filter { it.deploymentStatus != "draft" })
                    start += pageSize
                } while (response.next != null)

                _uiState.update {
                    it.copy(
                        assets = allAssets,
                        isLoadingAssets = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingAssets = false,
                        assetsError = e.message ?: "Failed to fetch forms"
                    )
                }
            }
        }
    }

    fun startLoginAndDownloadProcess() {
        val state = _uiState.value
        val asset = state.selectedAsset ?: return
        authCredentials.set(
            username = state.username.trim(),
            password = state.password,
            assetUid = asset.uid,
            serverUrl = state.serverUrl.trim()
        )
    }
}
