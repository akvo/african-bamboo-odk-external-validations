package com.akvo.externalodk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akvo.externalodk.ui.model.SubmissionUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class HomeUiState(
    val submissions: List<SubmissionUiModel> = emptyList(),
    val filteredSubmissions: List<SubmissionUiModel> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val currentPage: Int = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            delay(500L) // Simulate initial load
            val initialData = generateMockSubmissions(page = 0)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    submissions = initialData,
                    filteredSubmissions = initialData,
                    currentPage = 0,
                    canLoadMore = true
                )
            }
        }
    }

    fun loadMore() {
        val currentState = _uiState.value
        if (currentState.isLoadingMore || !currentState.canLoadMore || currentState.isSearchActive) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }

            // TODO: Replace with actual SQLite query using Room
            delay(1000L) // Simulate network/database delay

            val nextPage = currentState.currentPage + 1
            val newItems = generateMockSubmissions(page = nextPage)
            val hasMore = nextPage < MAX_PAGES - 1

            _uiState.update { state ->
                val updatedSubmissions = state.submissions + newItems
                state.copy(
                    isLoadingMore = false,
                    submissions = updatedSubmissions,
                    filteredSubmissions = if (state.searchQuery.isBlank()) {
                        updatedSubmissions
                    } else {
                        filterSubmissions(updatedSubmissions, state.searchQuery)
                    },
                    currentPage = nextPage,
                    canLoadMore = hasMore
                )
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredSubmissions = filterSubmissions(state.submissions, query)
            )
        }
    }

    fun onSearchActiveChange(active: Boolean) {
        _uiState.update { state ->
            if (!active) {
                state.copy(
                    isSearchActive = false,
                    searchQuery = "",
                    filteredSubmissions = state.submissions
                )
            } else {
                state.copy(isSearchActive = true)
            }
        }
    }

    private fun filterSubmissions(
        submissions: List<SubmissionUiModel>,
        query: String
    ): List<SubmissionUiModel> {
        // TODO: Replace with actual SQLite query using Room
        if (query.isBlank()) {
            return submissions
        }
        val lowerQuery = query.lowercase()
        return submissions.filter { submission ->
            submission.submittedBy.lowercase().contains(lowerQuery) ||
                submission.uuid.lowercase().contains(lowerQuery) ||
                submission.submissionTime.lowercase().contains(lowerQuery)
        }
    }

    private fun generateMockSubmissions(page: Int): List<SubmissionUiModel> {
        // TODO: Replace with actual SQLite query using Room
        val users = listOf("ifirmawan", "john.doe", "maria.santos", "akvo.user", "sarah.chen")
        return (0 until PAGE_SIZE).map { index ->
            val globalIndex = page * PAGE_SIZE + index
            SubmissionUiModel(
                uuid = UUID.randomUUID().toString(),
                submittedBy = users[globalIndex % users.size],
                submissionTime = "2026-01-${21 - (globalIndex / 3).coerceAtMost(20)} ${(9 + index) % 24}:${(globalIndex * 7) % 60}".padTimeComponents(),
                isSynced = globalIndex % 5 != 0 // Some items show as pending
            )
        }
    }

    private fun String.padTimeComponents(): String {
        val parts = this.split(" ")
        if (parts.size != 2) return this
        val timeParts = parts[1].split(":")
        if (timeParts.size != 2) return this
        val hour = timeParts[0].padStart(2, '0')
        val minute = timeParts[1].padStart(2, '0')
        return "${parts[0]} $hour:$minute"
    }

    companion object {
        private const val PAGE_SIZE = 10
        private const val MAX_PAGES = 5 // Total 50 mock items

        val mockSubmissions = listOf(
            SubmissionUiModel(
                uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                submittedBy = "ifirmawan",
                submissionTime = "2026-01-21 09:30",
                isSynced = true
            ),
            SubmissionUiModel(
                uuid = "b2c3d4e5-f6a7-8901-bcde-f12345678901",
                submittedBy = "john.doe",
                submissionTime = "2026-01-21 08:15",
                isSynced = true
            ),
            SubmissionUiModel(
                uuid = "c3d4e5f6-a7b8-9012-cdef-123456789012",
                submittedBy = "maria.santos",
                submissionTime = "2026-01-20 16:45",
                isSynced = true
            ),
            SubmissionUiModel(
                uuid = "d4e5f6a7-b8c9-0123-def0-234567890123",
                submittedBy = "ifirmawan",
                submissionTime = "2026-01-20 14:20",
                isSynced = true
            ),
            SubmissionUiModel(
                uuid = "e5f6a7b8-c9d0-1234-ef01-345678901234",
                submittedBy = "akvo.user",
                submissionTime = "2026-01-19 11:00",
                isSynced = true
            )
        )
    }
}
