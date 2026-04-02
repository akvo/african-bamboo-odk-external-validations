package org.akvo.afribamodkvalidator.data.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.akvo.afribamodkvalidator.validation.BlurDetector
import org.akvo.afribamodkvalidator.validation.OverlapChecker
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.validationSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "validation_settings"
)

data class ValidationSettings(
    val ocrWarnThreshold: Double = BlurDetector.DEFAULT_OCR_WARN,
    val ocrBlockThreshold: Double = BlurDetector.DEFAULT_OCR_BLOCK,
    val laplacianWarnThreshold: Double = BlurDetector.DEFAULT_LAP_WARN,
    val laplacianBlockThreshold: Double = BlurDetector.DEFAULT_LAP_BLOCK,
    val overlapThreshold: Double = OverlapChecker.DEFAULT_OVERLAP_THRESHOLD_PERCENT
)

@Singleton
class ValidationSettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dataStore = context.validationSettingsDataStore

    val settingsFlow: Flow<ValidationSettings> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.e(TAG, "DataStore read failed, using defaults", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs ->
        ValidationSettings(
            ocrWarnThreshold = prefs[KEY_OCR_WARN] ?: BlurDetector.DEFAULT_OCR_WARN,
            ocrBlockThreshold = prefs[KEY_OCR_BLOCK] ?: BlurDetector.DEFAULT_OCR_BLOCK,
            laplacianWarnThreshold = prefs[KEY_LAP_WARN] ?: BlurDetector.DEFAULT_LAP_WARN,
            laplacianBlockThreshold = prefs[KEY_LAP_BLOCK] ?: BlurDetector.DEFAULT_LAP_BLOCK,
            overlapThreshold = prefs[KEY_OVERLAP]
                ?: OverlapChecker.DEFAULT_OVERLAP_THRESHOLD_PERCENT
        )
    }

    suspend fun getSettings(): ValidationSettings = settingsFlow.first()

    suspend fun updateOcrWarnThreshold(value: Double) {
        dataStore.edit { it[KEY_OCR_WARN] = value }
    }

    suspend fun updateOcrBlockThreshold(value: Double) {
        dataStore.edit { it[KEY_OCR_BLOCK] = value }
    }

    suspend fun updateLaplacianWarnThreshold(value: Double) {
        dataStore.edit { it[KEY_LAP_WARN] = value }
    }

    suspend fun updateLaplacianBlockThreshold(value: Double) {
        dataStore.edit { it[KEY_LAP_BLOCK] = value }
    }

    suspend fun updateOverlapThreshold(value: Double) {
        dataStore.edit { it[KEY_OVERLAP] = value }
    }

    suspend fun resetToDefaults() {
        dataStore.edit { it.clear() }
    }

    companion object {
        private const val TAG = "ValidationSettings"
        private val KEY_OCR_WARN = doublePreferencesKey("ocr_warn_threshold")
        private val KEY_OCR_BLOCK = doublePreferencesKey("ocr_block_threshold")
        private val KEY_LAP_WARN = doublePreferencesKey("lap_warn_threshold")
        private val KEY_LAP_BLOCK = doublePreferencesKey("lap_block_threshold")
        private val KEY_OVERLAP = doublePreferencesKey("overlap_threshold")
    }
}
