package org.akvo.afribamodkvalidator.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.akvo.afribamodkvalidator.BuildConfig
import org.akvo.afribamodkvalidator.data.repository.AppUpdateRepository
import org.akvo.afribamodkvalidator.data.repository.UpdateResult
import javax.inject.Inject

sealed class UpdateUiState {
    data object Idle : UpdateUiState()
    data object Checking : UpdateUiState()
    data class Available(
        val version: String,
        val releaseNotes: String?,
        val apkUrl: String,
        val apkSizeMb: String,
        val htmlUrl: String,
        val isMetered: Boolean
    ) : UpdateUiState()

    data object Downloading : UpdateUiState()
    data object UpToDate : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val appUpdateRepository: AppUpdateRepository
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    private var hasCheckedOnLaunch = false
    private var checkJob: Job? = null

    fun checkForUpdate(isManual: Boolean = false) {
        if (!isManual && hasCheckedOnLaunch) return
        if (checkJob?.isActive == true ||
            _updateState.value is UpdateUiState.Downloading
        ) return
        if (!isManual) hasCheckedOnLaunch = true

        checkJob = viewModelScope.launch {
            _updateState.value = if (isManual) UpdateUiState.Checking else UpdateUiState.Idle

            when (val result = appUpdateRepository.checkForUpdate()) {
                is UpdateResult.Available -> {
                    val sizeMb = "%.1f".format(result.apkSize / (1024.0 * 1024.0))
                    _updateState.value = UpdateUiState.Available(
                        version = result.release.tagName.removePrefix("v"),
                        releaseNotes = result.release.body,
                        apkUrl = result.apkUrl,
                        apkSizeMb = sizeMb,
                        htmlUrl = result.release.htmlUrl,
                        isMetered = appUpdateRepository.isOnMeteredConnection()
                    )
                }

                is UpdateResult.UpToDate -> {
                    _updateState.value = if (isManual) {
                        UpdateUiState.UpToDate
                    } else {
                        UpdateUiState.Idle
                    }
                }

                is UpdateResult.Error -> {
                    _updateState.value = if (isManual) {
                        UpdateUiState.Error(result.message)
                    } else {
                        UpdateUiState.Idle
                    }
                }
            }
        }
    }

    fun downloadAndInstall(apkUrl: String) {
        _updateState.value = UpdateUiState.Downloading
        val fileName = "${AppUpdateRepository.APK_FILE_PREFIX}${System.currentTimeMillis()}.apk"

        appUpdateRepository.registerDownloadCompleteReceiver { downloadId ->
            val installed = appUpdateRepository.installApk(downloadId)
            appUpdateRepository.unregisterDownloadCompleteReceiver()
            _updateState.value = if (installed) {
                UpdateUiState.Idle
            } else {
                UpdateUiState.Error("Download failed. Please try again.")
            }
        }

        try {
            appUpdateRepository.downloadApk(apkUrl, fileName)
        } catch (e: Exception) {
            appUpdateRepository.unregisterDownloadCompleteReceiver()
            _updateState.value = UpdateUiState.Error(
                e.message ?: "Failed to start download"
            )
        }
    }

    fun dismissUpdate() {
        _updateState.value = UpdateUiState.Idle
    }

    fun currentVersion(): String = BuildConfig.VERSION_NAME

    override fun onCleared() {
        super.onCleared()
        appUpdateRepository.unregisterDownloadCompleteReceiver()
    }
}
