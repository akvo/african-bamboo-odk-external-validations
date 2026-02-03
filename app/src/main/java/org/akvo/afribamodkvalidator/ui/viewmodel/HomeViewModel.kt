package org.akvo.afribamodkvalidator.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.akvo.afribamodkvalidator.data.dao.FormMetadataDao
import org.akvo.afribamodkvalidator.data.dao.SubmissionDao
import org.akvo.afribamodkvalidator.data.entity.SubmissionEntity
import org.akvo.afribamodkvalidator.data.network.AuthCredentials
import org.akvo.afribamodkvalidator.ui.model.SubmissionUiModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class SortOption {
    NAME_ASC,
    NAME_DESC,
    DATE_NEWEST,
    DATE_OLDEST
}

data class HomeUiState(
    val submissions: List<SubmissionUiModel> = emptyList(),
    val filteredSubmissions: List<SubmissionUiModel> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isLoading: Boolean = false,
    val sortOption: SortOption = SortOption.DATE_NEWEST,
    val showSortSheet: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val submissionDao: SubmissionDao,
    private val formMetadataDao: FormMetadataDao,
    private val authCredentials: AuthCredentials
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadSubmissions()
    }

    private fun loadSubmissions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val assetUid = authCredentials.assetUid
            if (assetUid.isBlank()) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            submissionDao.getSubmissions(assetUid).collect { entities ->
                val uiModels = entities.map { it.toUiModel() }
                _uiState.update { state ->
                    val sorted = sortSubmissions(uiModels, state.sortOption)
                    state.copy(
                        isLoading = false,
                        submissions = uiModels,
                        filteredSubmissions = if (state.searchQuery.isBlank()) {
                            sorted
                        } else {
                            filterSubmissions(sorted, state.searchQuery)
                        }
                    )
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { state ->
            val sorted = sortSubmissions(state.submissions, state.sortOption)
            state.copy(
                searchQuery = query,
                filteredSubmissions = filterSubmissions(sorted, query)
            )
        }
    }

    fun onSortOptionChange(option: SortOption) {
        _uiState.update { state ->
            val sorted = sortSubmissions(state.submissions, option)
            state.copy(
                sortOption = option,
                showSortSheet = false,
                filteredSubmissions = if (state.searchQuery.isBlank()) {
                    sorted
                } else {
                    filterSubmissions(sorted, state.searchQuery)
                }
            )
        }
    }

    fun onShowSortSheet(show: Boolean) {
        _uiState.update { it.copy(showSortSheet = show) }
    }

    fun onSearchActiveChange(active: Boolean) {
        _uiState.update { state ->
            if (!active) {
                val sorted = sortSubmissions(state.submissions, state.sortOption)
                state.copy(
                    isSearchActive = false,
                    searchQuery = "",
                    filteredSubmissions = sorted
                )
            } else {
                state.copy(isSearchActive = true)
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            submissionDao.deleteAll()
            formMetadataDao.deleteAll()
            authCredentials.clear()
            onComplete()
        }
    }

    private fun filterSubmissions(
        submissions: List<SubmissionUiModel>,
        query: String
    ): List<SubmissionUiModel> {
        if (query.isBlank()) {
            return submissions
        }
        val lowerQuery = query.lowercase()
        return submissions.filter { submission ->
            submission.displayTitle.lowercase().contains(lowerQuery) ||
                submission.uuid.lowercase().contains(lowerQuery) ||
                submission.syncedOnText.lowercase().contains(lowerQuery)
        }
    }

    private fun sortSubmissions(
        submissions: List<SubmissionUiModel>,
        sortOption: SortOption
    ): List<SubmissionUiModel> {
        return when (sortOption) {
            SortOption.NAME_ASC -> submissions.sortedBy { it.displayTitle.lowercase() }
            SortOption.NAME_DESC -> submissions.sortedByDescending { it.displayTitle.lowercase() }
            SortOption.DATE_NEWEST -> submissions.sortedByDescending { it.submissionTimestamp }
            SortOption.DATE_OLDEST -> submissions.sortedBy { it.submissionTimestamp }
        }
    }

    private fun SubmissionEntity.toUiModel(): SubmissionUiModel {
        val instant = Instant.ofEpochMilli(submissionTime)
        val zonedDateTime = instant.atZone(ZoneId.systemDefault())

        // Format: "Synced on Tue, Jan 21, 2026 at 09:30"
        val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy")
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val syncedOnText = "Synced on ${dateFormatter.format(zonedDateTime)} at ${timeFormatter.format(zonedDateTime)}"

        // Use instanceName if available, otherwise fallback to formatted date
        val fallbackTitle = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(zonedDateTime)
        val displayTitle = instanceName ?: fallbackTitle

        return SubmissionUiModel(
            uuid = _uuid,
            displayTitle = displayTitle,
            syncedOnText = syncedOnText,
            submissionTimestamp = submissionTime,
            isSynced = true
        )
    }
}
