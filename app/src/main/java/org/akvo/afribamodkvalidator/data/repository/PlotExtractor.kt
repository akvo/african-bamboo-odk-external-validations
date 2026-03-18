package org.akvo.afribamodkvalidator.data.repository

import android.util.Log
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
 * Polygon field paths are discovered dynamically from the Kobo asset detail API
 * and passed as a parameter. Plot name uses instanceName with _id fallback.
 */
@Singleton
class PlotExtractor @Inject constructor() {

    private val polygonValidator = PolygonValidator()
    private val overlapChecker = OverlapChecker()
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /**
     * Extract a PlotEntity from a submission's rawData.
     *
     * @param submission The submission to extract plot data from
     * @param polygonFields Ordered list of field paths to try for polygon data (from asset detail)
     * @return PlotEntity if extraction succeeds, null if required data is missing
     */
    fun extractPlot(submission: SubmissionEntity, polygonFields: List<String>): PlotEntity? {
        return try {
            val rawData = json.parseToJsonElement(submission.rawData) as? JsonObject
                ?: return null

            // Try to extract polygon data from discovered field paths
            val polygonData = extractPolygonData(rawData, polygonFields) ?: return null

            // Parse polygon to JTS
            val jtsPolygon = polygonValidator.parseToJtsPolygon(polygonData)
                ?: return null

            // Convert to WKT and compute bounding box
            val wkt = overlapChecker.toWkt(jtsPolygon)
            val bbox = overlapChecker.computeBoundingBox(jtsPolygon)

            // Plot name: instanceName → _id
            val plotName = submission.instanceName ?: submission._id

            // Extract region info (stable field names across African Bamboo forms)
            val region = rawData.extractString(REGION_FIELD) ?: ""
            val subRegion = rawData.extractString(SUB_REGION_FIELD) ?: ""

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
     * Try to extract polygon data from field paths.
     * Checks fields in order, returns first non-empty match.
     */
    private fun extractPolygonData(rawData: JsonObject, polygonFields: List<String>): String? {
        for (field in polygonFields) {
            val value = rawData.extractString(field)
            if (!value.isNullOrBlank()) {
                return value
            }
        }
        return null
    }

    private fun JsonObject.extractString(key: String): String? {
        return this[key]?.jsonPrimitive?.contentOrNull
    }

    companion object {
        private const val TAG = "PlotExtractor"
        private const val REGION_FIELD = "woreda"
        private const val SUB_REGION_FIELD = "kebele"
    }
}
