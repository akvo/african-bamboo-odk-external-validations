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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.akvo.afribamodkvalidator.data.dao.SubmissionDao
import org.akvo.afribamodkvalidator.navigation.GeoMapView
import org.akvo.afribamodkvalidator.validation.GeoValue
import org.akvo.afribamodkvalidator.validation.GeoValueParser
import javax.inject.Inject

data class GeoMapViewUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val geoValue: GeoValue? = null,
    val error: String? = null
)

@HiltViewModel
class GeoMapViewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val submissionDao: SubmissionDao
) : ViewModel() {

    private val route = savedStateHandle.toRoute<GeoMapView>()
    private val uuid = route.uuid
    private val fieldKey = route.fieldKey

    private val _uiState = MutableStateFlow(GeoMapViewUiState())
    val uiState: StateFlow<GeoMapViewUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        loadGeoData()
    }

    private fun loadGeoData() {
        viewModelScope.launch {
            try {
                val submission = submissionDao.getByUuid(uuid)
                if (submission == null) {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Submission not found")
                    }
                    return@launch
                }

                val jsonObject = json.decodeFromString<JsonObject>(submission.rawData)
                val rawValue = jsonObject[fieldKey]
                if (rawValue == null || rawValue !is JsonPrimitive) {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Field \"$fieldKey\" not found")
                    }
                    return@launch
                }

                val geoValue = GeoValueParser.parse(rawValue.content)
                if (geoValue == null) {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Could not parse geo data")
                    }
                    return@launch
                }

                val title = fieldKey
                    .replace("_", " ")
                    .replace("/", " / ")
                    .split(" ")
                    .joinToString(" ") { word ->
                        word.replaceFirstChar { it.uppercase() }
                    }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        title = title,
                        geoValue = geoValue
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load geo data"
                    )
                }
            }
        }
    }
}
