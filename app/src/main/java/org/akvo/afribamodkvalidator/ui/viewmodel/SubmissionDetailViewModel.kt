package org.akvo.afribamodkvalidator.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.akvo.afribamodkvalidator.data.dao.SubmissionDao
import org.akvo.afribamodkvalidator.navigation.SubmissionDetail
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class SubmissionDetailUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val submittedOn: String = "",
    val submittedBy: String = "",
    val answers: List<AnswerItem> = emptyList(),
    val error: String? = null
)

data class AnswerItem(
    val label: String,
    val value: String
)

@HiltViewModel
class SubmissionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val submissionDao: SubmissionDao
) : ViewModel() {

    private val route = savedStateHandle.toRoute<SubmissionDetail>()
    private val uuid = route.uuid

    private val _uiState = MutableStateFlow(SubmissionDetailUiState())
    val uiState: StateFlow<SubmissionDetailUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        loadSubmission()
    }

    private fun loadSubmission() {
        viewModelScope.launch {
            try {
                val submission = submissionDao.getByUuid(uuid)
                if (submission == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Submission not found"
                        )
                    }
                    return@launch
                }

                // Format submission time
                val instant = Instant.ofEpochMilli(submission.submissionTime)
                val zonedDateTime = instant.atZone(ZoneId.systemDefault())
                val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy 'at' HH:mm")
                val submittedOn = dateFormatter.format(zonedDateTime)

                // Parse rawData JSON to extract answers
                val answers = parseRawData(submission.rawData)

                // Use instanceName or formatted date as title
                val fallbackTitle = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(zonedDateTime)
                val title = submission.instanceName ?: fallbackTitle

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        title = title,
                        submittedOn = submittedOn,
                        submittedBy = submission.submittedBy ?: "Unknown",
                        answers = answers
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load submission"
                    )
                }
            }
        }
    }

    private fun parseRawData(rawData: String): List<AnswerItem> {
        return try {
            val jsonObject = json.decodeFromString<JsonObject>(rawData)
            jsonObject.entries
                .filter { !it.key.startsWith("_") && it.key != "meta" && it.key != "formhub" }
                .map { (key, value) ->
                    AnswerItem(
                        label = formatLabel(key),
                        value = formatValue(value)
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun formatLabel(key: String): String {
        return key
            .replace("_", " ")
            .replace("/", " / ")
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
    }

    private fun formatValue(element: JsonElement): String {
        return when (element) {
            is JsonNull -> "-"
            is JsonPrimitive -> element.content.ifBlank { "-" }
            is JsonArray -> element.joinToString(", ") { formatValue(it) }.ifBlank { "-" }
            is JsonObject -> element.entries.joinToString("; ") { "${it.key}: ${formatValue(it.value)}" }.ifBlank { "-" }
        }
    }
}
