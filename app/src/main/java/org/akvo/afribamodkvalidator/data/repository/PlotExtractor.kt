package org.akvo.afribamodkvalidator.data.repository

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.akvo.afribamodkvalidator.data.entity.PlotEntity
import org.akvo.afribamodkvalidator.data.entity.SubmissionEntity
import org.akvo.afribamodkvalidator.validation.OverlapChecker
import org.akvo.afribamodkvalidator.validation.PolygonValidator
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts PlotEntity from synced submission rawData.
 *
 * Parses the dynamic JSON to find polygon data and farmer information,
 * then converts to a PlotEntity ready for database insertion.
 *
 * Field mappings are loaded from assets/plot_extraction_config.json.
 * Configuration is pre-loaded during initialization to ensure predictable
 * extraction timing during sync operations.
 */
@Singleton
class PlotExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val polygonValidator = PolygonValidator()
    private val overlapChecker = OverlapChecker()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Configuration is loaded eagerly during initialization to avoid
     * asset loading overhead during the first sync operation.
     */
    private val config: PlotExtractionConfig = loadConfig()

    init {
        Log.d(TAG, "PlotExtractor initialized with config: " +
                "polygonFields=${config.polygonFields.size}, " +
                "plotNameFields=${config.plotNameFields.size}, " +
                "regionField=${config.regionField}, " +
                "subRegionField=${config.subRegionField}")
    }

    private fun loadConfig(): PlotExtractionConfig {
        val startTime = System.currentTimeMillis()
        return try {
            val configJson = context.assets.open(CONFIG_FILE).bufferedReader().use { it.readText() }
            val loadedConfig = json.decodeFromString<PlotExtractionConfig>(configJson)
            val elapsedMs = System.currentTimeMillis() - startTime
            Log.d(TAG, "Loaded plot extraction config from $CONFIG_FILE in ${elapsedMs}ms")
            loadedConfig
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load plot extraction config, using defaults", e)
            DEFAULT_CONFIG
        }
    }

    /**
     * Extract a PlotEntity from a submission's rawData.
     *
     * @return PlotEntity if extraction succeeds, null if required data is missing
     */
    fun extractPlot(submission: SubmissionEntity): PlotEntity? {
        return try {
            val rawData = json.parseToJsonElement(submission.rawData) as? JsonObject
                ?: return null

            // Try to extract polygon data from configured field paths
            val polygonData = extractPolygonData(rawData) ?: return null

            // Parse polygon to JTS
            val jtsPolygon = polygonValidator.parseToJtsPolygon(polygonData)
                ?: return null

            // Convert to WKT and compute bounding box
            val wkt = overlapChecker.toWkt(jtsPolygon)
            val bbox = overlapChecker.computeBoundingBox(jtsPolygon)

            // Extract plot name from configured fields
            val plotName = buildPlotName(rawData)

            // Extract region info from configured fields
            val region = rawData.extractString(config.regionField) ?: ""
            val subRegion = rawData.extractString(config.subRegionField) ?: ""

            // Use submission's instanceName or generate from uuid
            val instanceName = submission.instanceName ?: submission._uuid

            PlotEntity(
                uuid = UUID.randomUUID().toString(),
                plotName = plotName,
                instanceName = instanceName,
                polygonWkt = wkt,
                minLat = bbox.minLat,
                maxLat = bbox.maxLat,
                minLon = bbox.minLon,
                maxLon = bbox.maxLon,
                isDraft = false,
                formId = submission.assetUid,
                region = region,
                subRegion = subRegion,
                submissionUuid = submission._uuid
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract plot from submission ${submission._uuid}", e)
            null
        }
    }

    /**
     * Try to extract polygon data from configured field paths.
     * Checks fields in order, returns first non-empty match.
     */
    private fun extractPolygonData(rawData: JsonObject): String? {
        for (field in config.polygonFields) {
            val value = rawData.extractString(field)
            if (!value.isNullOrBlank()) {
                return value
            }
        }
        return null
    }

    /**
     * Build plot name from configured name fields.
     * Joins non-empty values with spaces.
     */
    private fun buildPlotName(rawData: JsonObject): String {
        return config.plotNameFields
            .mapNotNull { rawData.extractString(it) }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Unknown" }
    }

    private fun JsonObject.extractString(key: String): String? {
        return this[key]?.jsonPrimitive?.contentOrNull
    }

    companion object {
        private const val TAG = "PlotExtractor"
        private const val CONFIG_FILE = "plot_extraction_config.json"

        private val DEFAULT_CONFIG = PlotExtractionConfig(
            polygonFields = listOf(
                "boundary_mapping/Open_Area_GeoMapping",
                "Open_Area_GeoMapping",
                "manual_boundary",
                "boundary_mapping/manual_boundary"
            ),
            plotNameFields = listOf(
                "First_Name",
                "Father_s_Name",
                "Grandfather_s_Name"
            ),
            regionField = "woreda",
            subRegionField = "kebele"
        )
    }
}
