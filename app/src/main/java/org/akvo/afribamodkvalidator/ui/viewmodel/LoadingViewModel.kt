package org.akvo.afribamodkvalidator.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
import org.akvo.afribamodkvalidator.data.dao.SubmissionDao
import org.akvo.afribamodkvalidator.data.network.AuthCredentials
import org.akvo.afribamodkvalidator.data.repository.KoboRepository
import org.akvo.afribamodkvalidator.data.repository.SyncProgress
import org.akvo.afribamodkvalidator.navigation.LoadingType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

sealed class LoadingResult {
    data object Loading : LoadingResult()
    data class DownloadSuccess(
        val totalEntries: Int,
        val latestSubmissionDate: String
    ) : LoadingResult()
    data class ResyncSuccess(
        val addedRecords: Int,
        val updatedRecords: Int,
        val rejectedRecords: Int,
        val latestRecordTimestamp: String
    ) : LoadingResult()
    data class Error(val message: String) : LoadingResult()
}

@HiltViewModel
class LoadingViewModel @Inject constructor(
    private val koboRepository: KoboRepository,
    private val submissionDao: SubmissionDao,
    private val authCredentials: AuthCredentials
) : ViewModel() {

    private val _loadingResult = MutableStateFlow<LoadingResult>(LoadingResult.Loading)
    val loadingResult: StateFlow<LoadingResult> = _loadingResult.asStateFlow()

    private val _syncProgress = MutableStateFlow<SyncProgress?>(null)
    val syncProgress: StateFlow<SyncProgress?> = _syncProgress.asStateFlow()

    private var hasStarted = false

    fun startLoading(loadingType: LoadingType) {
        if (hasStarted) return
        hasStarted = true
        performLoading(loadingType)
    }

    fun retry(loadingType: LoadingType) {
        _loadingResult.value = LoadingResult.Loading
        performLoading(loadingType)
    }

    private fun performLoading(loadingType: LoadingType) {
        viewModelScope.launch {
            val assetUid = authCredentials.assetUid
            if (assetUid.isBlank()) {
                _loadingResult.value = LoadingResult.Error("No form ID configured")
                return@launch
            }

            when (loadingType) {
                LoadingType.DOWNLOAD -> performDownload(assetUid)
                LoadingType.RESYNC -> performResync(assetUid)
            }
        }
    }

    private suspend fun performDownload(assetUid: String) {
        try {
            koboRepository.fetchSubmissions(assetUid).collect { progress ->
                _syncProgress.value = progress
                if (progress is SyncProgress.Complete) {
                    val latestTimestamp = submissionDao.getLatestSubmissionTime(assetUid)
                    val formattedDate = formatTimestamp(latestTimestamp)
                    _loadingResult.value = LoadingResult.DownloadSuccess(
                        totalEntries = progress.inserted,
                        latestSubmissionDate = formattedDate
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _loadingResult.value = LoadingResult.Error(
                e.message ?: "Download failed"
            )
        }
    }

    private suspend fun performResync(assetUid: String) {
        try {
            koboRepository.resync(assetUid).collect { progress ->
                _syncProgress.value = progress
                if (progress is SyncProgress.Complete) {
                    val latestTimestamp = submissionDao.getLatestSubmissionTime(assetUid)
                    val formattedDate = formatTimestamp(latestTimestamp)
                    _loadingResult.value = LoadingResult.ResyncSuccess(
                        addedRecords = progress.inserted + progress.restored,
                        updatedRecords = 0,
                        rejectedRecords = progress.rejected,
                        latestRecordTimestamp = formattedDate
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _loadingResult.value = LoadingResult.Error(
                e.message ?: "Sync failed"
            )
        }
    }

    private fun formatTimestamp(timestamp: Long?): String {
        if (timestamp == null) return "No data"
        val instant = Instant.ofEpochMilli(timestamp)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }
}
