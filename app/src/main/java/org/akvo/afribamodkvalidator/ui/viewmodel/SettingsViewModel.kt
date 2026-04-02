package org.akvo.afribamodkvalidator.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.akvo.afribamodkvalidator.data.settings.ValidationSettingsDataStore
import org.akvo.afribamodkvalidator.validation.BlurDetector
import org.akvo.afribamodkvalidator.validation.OverlapChecker
import javax.inject.Inject

data class SettingsUiState(
    val ocrWarnThreshold: Double = BlurDetector.DEFAULT_OCR_WARN,
    val ocrBlockThreshold: Double = BlurDetector.DEFAULT_OCR_BLOCK,
    val laplacianWarnThreshold: Double = BlurDetector.DEFAULT_LAP_WARN,
    val laplacianBlockThreshold: Double = BlurDetector.DEFAULT_LAP_BLOCK,
    val overlapThreshold: Double = OverlapChecker.DEFAULT_OVERLAP_THRESHOLD_PERCENT,
    val isLoading: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: ValidationSettingsDataStore
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = settingsDataStore.settingsFlow
        .map { settings ->
            SettingsUiState(
                ocrWarnThreshold = settings.ocrWarnThreshold,
                ocrBlockThreshold = settings.ocrBlockThreshold,
                laplacianWarnThreshold = settings.laplacianWarnThreshold,
                laplacianBlockThreshold = settings.laplacianBlockThreshold,
                overlapThreshold = settings.overlapThreshold,
                isLoading = false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState()
        )

    fun updateOcrWarnThreshold(value: Double) {
        viewModelScope.launch { settingsDataStore.updateOcrWarnThreshold(value) }
    }

    fun updateOcrBlockThreshold(value: Double) {
        viewModelScope.launch { settingsDataStore.updateOcrBlockThreshold(value) }
    }

    fun updateLaplacianWarnThreshold(value: Double) {
        viewModelScope.launch { settingsDataStore.updateLaplacianWarnThreshold(value) }
    }

    fun updateLaplacianBlockThreshold(value: Double) {
        viewModelScope.launch { settingsDataStore.updateLaplacianBlockThreshold(value) }
    }

    fun updateOverlapThreshold(value: Double) {
        viewModelScope.launch { settingsDataStore.updateOverlapThreshold(value) }
    }

    fun resetToDefaults() {
        viewModelScope.launch { settingsDataStore.resetToDefaults() }
    }
}
