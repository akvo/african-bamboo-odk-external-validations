package org.akvo.afribamodkvalidator.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.akvo.afribamodkvalidator.data.settings.ValidationSettings
import org.akvo.afribamodkvalidator.data.settings.ValidationSettingsDataStore
import javax.inject.Inject

data class SettingsUiState(
    val blurWarnThreshold: Double = 100.0,
    val blurBlockThreshold: Double = 50.0,
    val overlapThreshold: Double = 20.0,
    val isLoading: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: ValidationSettingsDataStore
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = settingsDataStore.settingsFlow
        .map { settings ->
            SettingsUiState(
                blurWarnThreshold = settings.blurWarnThreshold,
                blurBlockThreshold = settings.blurBlockThreshold,
                overlapThreshold = settings.overlapThreshold,
                isLoading = false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState()
        )

    fun updateBlurWarnThreshold(value: Double) {
        viewModelScope.launch {
            val currentBlock = settingsDataStore.getSettings().blurBlockThreshold
            val safeValue = value.coerceAtLeast(currentBlock + 1.0)
            settingsDataStore.updateBlurWarnThreshold(safeValue)
        }
    }

    fun updateBlurBlockThreshold(value: Double) {
        viewModelScope.launch {
            val currentWarn = settingsDataStore.getSettings().blurWarnThreshold
            val safeValue = value.coerceAtMost(currentWarn - 1.0)
            settingsDataStore.updateBlurBlockThreshold(safeValue)
        }
    }

    fun updateOverlapThreshold(value: Double) {
        viewModelScope.launch {
            settingsDataStore.updateOverlapThreshold(value)
        }
    }
}
