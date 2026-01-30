package org.akvo.afribamodkvalidator.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.akvo.afribamodkvalidator.data.dao.SubmissionDao
import org.akvo.afribamodkvalidator.data.network.AuthCredentials
import org.akvo.afribamodkvalidator.data.repository.KoboRepository
import org.akvo.afribamodkvalidator.navigation.LoadingType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
        val countBefore = submissionDao.getCount(assetUid)

        koboRepository.fetchSubmissions(assetUid)
            .onSuccess { totalFetched ->
                val latestTimestamp = submissionDao.getLatestSubmissionTime(assetUid)
                val formattedDate = formatTimestamp(latestTimestamp)

                _loadingResult.value = LoadingResult.DownloadSuccess(
                    totalEntries = totalFetched,
                    latestSubmissionDate = formattedDate
                )
            }
            .onFailure { error ->
                _loadingResult.value = LoadingResult.Error(
                    error.message ?: "Download failed"
                )
            }
    }

    private suspend fun performResync(assetUid: String) {
        val countBefore = submissionDao.getCount(assetUid)

        koboRepository.resync(assetUid)
            .onSuccess { totalFetched ->
                val latestTimestamp = submissionDao.getLatestSubmissionTime(assetUid)
                val formattedDate = formatTimestamp(latestTimestamp)

                _loadingResult.value = LoadingResult.ResyncSuccess(
                    addedRecords = totalFetched,
                    updatedRecords = 0,
                    latestRecordTimestamp = formattedDate
                )
            }
            .onFailure { error ->
                _loadingResult.value = LoadingResult.Error(
                    error.message ?: "Sync failed"
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
