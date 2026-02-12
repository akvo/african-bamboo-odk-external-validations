package org.akvo.afribamodkvalidator.data.repository

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import org.akvo.afribamodkvalidator.data.model.OfflineRegion
import org.akvo.afribamodkvalidator.data.model.OfflineRegionsConfig
import org.akvo.afribamodkvalidator.data.model.toDomain
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineRegionRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadRegions(): List<OfflineRegion> {
        return try {
            val jsonString = context.assets
                .open(REGIONS_FILE)
                .bufferedReader()
                .use { it.readText() }

            val config = json.decodeFromString<OfflineRegionsConfig>(jsonString)
            config.regions.map { it.toDomain() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load offline regions", e)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "OfflineRegionRepository"
        private const val REGIONS_FILE = "offline_regions.json"
    }
}
