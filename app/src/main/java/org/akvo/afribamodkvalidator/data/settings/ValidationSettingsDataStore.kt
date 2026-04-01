package org.akvo.afribamodkvalidator.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.akvo.afribamodkvalidator.validation.BlurDetector
import org.akvo.afribamodkvalidator.validation.OverlapChecker
import javax.inject.Inject
import javax.inject.Singleton

private val Context.validationSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "validation_settings"
)

data class ValidationSettings(
    val blurWarnThreshold: Double = BlurDetector.DEFAULT_WARN_THRESHOLD,
    val blurBlockThreshold: Double = BlurDetector.DEFAULT_BLOCK_THRESHOLD,
    val maxDimension: Int = BlurDetector.DEFAULT_MAX_DIMENSION,
    val overlapThreshold: Double = OverlapChecker.DEFAULT_OVERLAP_THRESHOLD_PERCENT
)

@Singleton
class ValidationSettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dataStore = context.validationSettingsDataStore

    val settingsFlow: Flow<ValidationSettings> = dataStore.data.map { prefs ->
        ValidationSettings(
            blurWarnThreshold = prefs[KEY_BLUR_WARN_THRESHOLD]
                ?: BlurDetector.DEFAULT_WARN_THRESHOLD,
            blurBlockThreshold = prefs[KEY_BLUR_BLOCK_THRESHOLD]
                ?: BlurDetector.DEFAULT_BLOCK_THRESHOLD,
            maxDimension = prefs[KEY_MAX_DIMENSION]
                ?: BlurDetector.DEFAULT_MAX_DIMENSION,
            overlapThreshold = prefs[KEY_OVERLAP_THRESHOLD]
                ?: OverlapChecker.DEFAULT_OVERLAP_THRESHOLD_PERCENT
        )
    }

    suspend fun getSettings(): ValidationSettings {
        return settingsFlow.first()
    }

    suspend fun updateBlurWarnThreshold(value: Double) {
        dataStore.edit { prefs ->
            prefs[KEY_BLUR_WARN_THRESHOLD] = value
        }
    }

    suspend fun updateBlurBlockThreshold(value: Double) {
        dataStore.edit { prefs ->
            prefs[KEY_BLUR_BLOCK_THRESHOLD] = value
        }
    }

    suspend fun updateOverlapThreshold(value: Double) {
        dataStore.edit { prefs ->
            prefs[KEY_OVERLAP_THRESHOLD] = value
        }
    }

    companion object {
        private val KEY_BLUR_WARN_THRESHOLD = doublePreferencesKey("blur_warn_threshold")
        private val KEY_BLUR_BLOCK_THRESHOLD = doublePreferencesKey("blur_block_threshold")
        private val KEY_MAX_DIMENSION = intPreferencesKey("max_dimension")
        private val KEY_OVERLAP_THRESHOLD = doublePreferencesKey("overlap_threshold")
    }
}
