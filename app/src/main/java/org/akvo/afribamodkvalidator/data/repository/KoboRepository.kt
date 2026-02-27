package org.akvo.afribamodkvalidator.data.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.akvo.afribamodkvalidator.data.dao.FormMetadataDao
import org.akvo.afribamodkvalidator.data.dao.PlotDao
import org.akvo.afribamodkvalidator.data.dao.SubmissionDao
import org.akvo.afribamodkvalidator.data.entity.FormMetadataEntity
import org.akvo.afribamodkvalidator.data.entity.SubmissionEntity
import org.akvo.afribamodkvalidator.data.network.KoboApiService
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "KoboRepository"
private const val VALIDATION_STATUS_NOT_APPROVED = "validation_status_not_approved"

@Singleton
class KoboRepository @Inject constructor(
    private val apiService: KoboApiService,
    private val submissionDao: SubmissionDao,
    private val formMetadataDao: FormMetadataDao,
    private val plotDao: PlotDao,
    private val plotExtractor: PlotExtractor
) {

    /**
     * Performs a delta sync, fetching only submissions newer than the last sync,
     * then reconciles validation status changes.
     * If no previous sync exists, falls back to full fetch.
     *
     * @return Flow emitting SyncProgress updates
     */
    fun resync(assetUid: String): Flow<SyncProgress> = flow {
        // If no previous sync, do a full fetch
        val lastSyncTimestamp = formMetadataDao.getLastSyncTimestamp(assetUid)
        if (lastSyncTimestamp == null) {
            emitAll(fetchSubmissionsInternal(assetUid))
            return@flow
        }

        var totalFetched = 0
        var totalRejected = 0
        var totalCount = 0
        var start = 0
        val pageSize = KoboApiService.DEFAULT_PAGE_SIZE

        // Step 1: Delta sync — fetch genuinely new submissions
        val lastSyncIso = formatTimestampToIso(lastSyncTimestamp)
        val query = """{"_submission_time": {"${"$"}gt": "$lastSyncIso"}}"""

        do {
            val response = apiService.getSubmissionsSince(
                assetUid = assetUid,
                query = query,
                limit = pageSize,
                start = start
            )

            if (totalCount == 0) totalCount = response.count

            val entities = response.results.mapNotNull { jsonObject ->
                if (isRejected(jsonObject)) {
                    totalRejected++
                    null
                } else {
                    transformToEntity(assetUid, jsonObject)
                }
            }

            if (entities.isNotEmpty()) {
                // Count only genuinely new submissions (not re-fetched due to timestamp truncation)
                val existingUuids = submissionDao.findExistingUuids(entities.map { it._uuid }).toSet()
                totalFetched += entities.count { it._uuid !in existingUuids }
                submissionDao.insertAll(entities)
            }

            start += pageSize
            emit(SyncProgress.Downloading(processed = start.coerceAtMost(totalCount), total = totalCount))
        } while (response.next != null)

        if (totalRejected > 0) {
            Log.d(TAG, "resync: Skipped $totalRejected rejected submissions from delta")
        }

        // Step 2: Validation reconciliation — catch status changes on old submissions
        val (reconcileRejected, reconcileRestored) = reconcileValidationChanges(assetUid, lastSyncTimestamp)
        totalRejected += reconcileRejected

        // Step 3: Post-processing — run if delta sync or reconciliation changed data
        val dataChanged = totalFetched > 0 || reconcileRestored > 0
        if (dataChanged) {
            matchDraftsToSubmissions()
            extractPlotsFromSubmissions(assetUid)
        }

        // Always advance sync timestamp after successful resync.
        // Uses current time to avoid re-fetching caused by sub-second timestamp
        // truncation (Kobo API returns seconds but compares with sub-second precision).
        formMetadataDao.insertOrUpdate(
            FormMetadataEntity(
                assetUid = assetUid,
                lastSyncTimestamp = System.currentTimeMillis()
            )
        )

        emit(SyncProgress.Complete(inserted = totalFetched, rejected = totalRejected, restored = reconcileRestored))
    }

    /**
     * Fetches all submissions for a form (initial download).
     *
     * @return Flow emitting SyncProgress updates
     */
    fun fetchSubmissions(assetUid: String): Flow<SyncProgress> = fetchSubmissionsInternal(assetUid)

    private fun fetchSubmissionsInternal(assetUid: String): Flow<SyncProgress> = flow {
        var totalFetched = 0
        var totalRejected = 0
        var totalCount = 0
        var start = 0
        val pageSize = KoboApiService.DEFAULT_PAGE_SIZE

        do {
            val response = apiService.getSubmissions(
                assetUid = assetUid,
                limit = pageSize,
                start = start
            )

            if (totalCount == 0) totalCount = response.count

            val entities = response.results.mapNotNull { jsonObject ->
                if (isRejected(jsonObject)) {
                    totalRejected++
                    null
                } else {
                    transformToEntity(assetUid, jsonObject)
                }
            }

            if (entities.isNotEmpty()) {
                // Count only genuinely new submissions (not re-fetched due to timestamp truncation)
                val existingUuids = submissionDao.findExistingUuids(entities.map { it._uuid }).toSet()
                totalFetched += entities.count { it._uuid !in existingUuids }
                submissionDao.insertAll(entities)
            }

            start += pageSize
            emit(SyncProgress.Downloading(processed = start.coerceAtMost(totalCount), total = totalCount))
        } while (response.next != null)

        if (totalRejected > 0) {
            Log.d(TAG, "fetchSubmissions: Skipped $totalRejected rejected submissions")
        }

        // Post-processing and timestamp update if we fetched anything
        if (totalFetched > 0) {
            formMetadataDao.insertOrUpdate(
                FormMetadataEntity(
                    assetUid = assetUid,
                    lastSyncTimestamp = System.currentTimeMillis()
                )
            )
            matchDraftsToSubmissions()
            extractPlotsFromSubmissions(assetUid)
        }

        emit(SyncProgress.Complete(inserted = totalFetched, rejected = totalRejected))
    }

    /**
     * Links draft plots to synced submissions by matching instanceName.
     *
     * Uses batch queries for efficiency:
     * - Fetches all drafts and their instanceNames
     * - Batch queries submissions matching those instanceNames
     * - Updates matching drafts (individual updates, but no N+1 query problem)
     *
     * For large datasets, this reduces database queries from O(2N) to O(2 + M),
     * where M is the number of matches found.
     */
    private suspend fun matchDraftsToSubmissions() {
        val startTime = System.currentTimeMillis()
        val drafts = plotDao.getAllDrafts()

        if (drafts.isEmpty()) {
            Log.d(TAG, "matchDraftsToSubmissions: No drafts to match")
            return
        }

        // Batch query: get all submissions with matching instanceNames
        val instanceNames = drafts.map { it.instanceName }
        val matchingSubmissions = submissionDao.findByInstanceNames(instanceNames)

        // Create lookup map for O(1) access
        val submissionByInstanceName = matchingSubmissions
            .filter { it.instanceName != null }
            .associateBy { it.instanceName!! }

        // Update matching drafts
        var matchedCount = 0
        for (draft in drafts) {
            val submission = submissionByInstanceName[draft.instanceName]
            if (submission != null) {
                plotDao.updateDraftStatus(
                    instanceName = draft.instanceName,
                    submissionUuid = submission._uuid
                )
                matchedCount++
            }
        }

        val elapsedMs = System.currentTimeMillis() - startTime
        Log.d(TAG, "matchDraftsToSubmissions: Processed ${drafts.size} drafts, " +
                "matched $matchedCount in ${elapsedMs}ms")
    }

    /**
     * Extracts plots from synced submissions that don't already have plots.
     *
     * Uses batch operations for efficiency:
     * - Batch queries existing plot submissionUuids to filter already-processed submissions
     * - Extracts plots from remaining submissions
     * - Batch inserts all new plots
     *
     * For large datasets, this reduces database queries from O(2N) to O(3),
     * regardless of dataset size.
     */
    private suspend fun extractPlotsFromSubmissions(assetUid: String) {
        val startTime = System.currentTimeMillis()
        val submissions = submissionDao.getSubmissionsSync(assetUid)

        if (submissions.isEmpty()) {
            Log.d(TAG, "extractPlotsFromSubmissions: No submissions for $assetUid")
            return
        }

        // Batch query: get all submissionUuids that already have plots
        val submissionUuids = submissions.map { it._uuid }
        val existingSubmissionUuids = plotDao.findExistingSubmissionUuids(submissionUuids).toSet()

        // Filter to submissions that need plot extraction
        val submissionsToProcess = submissions.filter { it._uuid !in existingSubmissionUuids }

        if (submissionsToProcess.isEmpty()) {
            val elapsedMs = System.currentTimeMillis() - startTime
            Log.d(TAG, "extractPlotsFromSubmissions: All ${submissions.size} submissions " +
                    "already have plots (${elapsedMs}ms)")
            return
        }

        // Extract plots from submissions
        val newPlots = submissionsToProcess.mapNotNull { submission ->
            plotExtractor.extractPlot(submission)
        }

        // Batch insert all new plots
        if (newPlots.isNotEmpty()) {
            plotDao.insertOrUpdateAll(newPlots)
        }

        val elapsedMs = System.currentTimeMillis() - startTime
        Log.d(TAG, "extractPlotsFromSubmissions: Processed ${submissionsToProcess.size} " +
                "submissions, extracted ${newPlots.size} plots in ${elapsedMs}ms " +
                "(skipped ${existingSubmissionUuids.size} existing)")
    }

    /**
     * Reconciles validation status changes since last sync.
     *
     * Uses lightweight query with fields parameter (~100 bytes/record) to detect:
     * - Newly rejected submissions → delete from local DB
     * - Re-approved submissions (previously rejected, now approved) → re-fetch and insert
     *
     * @return pair of (rejected count, restored count)
     */
    private suspend fun reconcileValidationChanges(assetUid: String, lastSyncTimestamp: Long): Pair<Int, Int> {
        val lastSyncEpoch = lastSyncTimestamp / 1000 // Convert millis to seconds for Kobo API
        val query = """{"_validation_status.timestamp": {"${"$"}gt": $lastSyncEpoch}}"""
        val fields = """["_uuid","_validation_status"]"""

        val rejectedUuids = mutableListOf<String>()
        val reApprovedUuids = mutableListOf<String>()
        var start = 0
        val pageSize = KoboApiService.DEFAULT_PAGE_SIZE

        do {
            val response = apiService.getSubmissionsWithFields(
                assetUid = assetUid,
                query = query,
                fields = fields,
                limit = pageSize,
                start = start
            )

            for (jsonObject in response.results) {
                val uuid = jsonObject.extractString("_uuid") ?: continue
                if (isRejected(jsonObject)) {
                    rejectedUuids.add(uuid)
                } else {
                    reApprovedUuids.add(uuid)
                }
            }

            start += pageSize
        } while (response.next != null)

        // Delete rejected submissions
        if (rejectedUuids.isNotEmpty()) {
            removeRejectedSubmissions(rejectedUuids)
            Log.d(TAG, "reconcileValidationChanges: Removed ${rejectedUuids.size} newly-rejected submissions")
        }

        // Re-fetch and insert re-approved submissions
        if (reApprovedUuids.isNotEmpty()) {
            restoreReApprovedSubmissions(assetUid, reApprovedUuids)
            Log.d(TAG, "reconcileValidationChanges: Restored ${reApprovedUuids.size} re-approved submissions")
        }

        return Pair(rejectedUuids.size, reApprovedUuids.size)
    }

    /**
     * Re-fetches full data for re-approved submissions and inserts them into the DB.
     */
    private suspend fun restoreReApprovedSubmissions(assetUid: String, uuids: List<String>) {
        // Fetch in batches to avoid query string length limits
        for (batch in uuids.chunked(50)) {
            val idList = batch.joinToString(",") { "\"$it\"" }
            val query = """{"_uuid": {"${"$"}in": [$idList]}}"""

            var start = 0
            val pageSize = KoboApiService.DEFAULT_PAGE_SIZE

            do {
                val response = apiService.getSubmissionsSince(
                    assetUid = assetUid,
                    query = query,
                    limit = pageSize,
                    start = start
                )

                val entities = response.results.mapNotNull { jsonObject ->
                    transformToEntity(assetUid, jsonObject)
                }

                if (entities.isNotEmpty()) {
                    submissionDao.insertAll(entities)
                }

                start += pageSize
            } while (response.next != null)
        }

        // Extract plots for restored submissions
        extractPlotsFromSubmissions(assetUid)
    }

    private fun isRejected(jsonObject: JsonObject): Boolean {
        val validationStatus = jsonObject["_validation_status"] as? JsonObject ?: return false
        val uid = validationStatus["uid"]?.jsonPrimitive?.contentOrNull ?: return false
        return uid == VALIDATION_STATUS_NOT_APPROVED
    }

    private suspend fun removeRejectedSubmissions(rejectedUuids: List<String>) {
        if (rejectedUuids.isEmpty()) return

        val startTime = System.currentTimeMillis()
        val plotsDeleted = plotDao.deleteBySubmissionUuids(rejectedUuids)
        val submissionsDeleted = submissionDao.deleteByUuids(rejectedUuids)
        val elapsedMs = System.currentTimeMillis() - startTime

        Log.d(TAG, "removeRejectedSubmissions: Deleted $submissionsDeleted submissions " +
                "and $plotsDeleted plots in ${elapsedMs}ms")
    }

    private fun transformToEntity(assetUid: String, jsonObject: JsonObject): SubmissionEntity? {
        val uuid = jsonObject.extractString("_uuid") ?: return null
        val id = jsonObject.extractId("_id") ?: return null
        val submissionTimeStr = jsonObject.extractString("_submission_time") ?: return null
        val submissionTime = parseSubmissionTime(submissionTimeStr) ?: return null
        val submittedBy = jsonObject.extractString("_submitted_by")
        val instanceName = jsonObject.extractString("meta/instanceName")

        // Extract system data (geolocation, tags)
        val systemData = buildSystemData(jsonObject)

        // Store the entire JSON as rawData
        val rawData = jsonObject.toString()

        return SubmissionEntity(
            _uuid = uuid,
            assetUid = assetUid,
            _id = id,
            submissionTime = submissionTime,
            submittedBy = submittedBy,
            instanceName = instanceName,
            rawData = rawData,
            systemData = systemData
        )
    }

    private fun buildSystemData(jsonObject: JsonObject): String? {
        val systemFields = mutableMapOf<String, Any?>()

        // Extract geolocation
        jsonObject["_geolocation"]?.let { geoElement ->
            if (geoElement is JsonArray) {
                val coords = geoElement.mapNotNull {
                    (it as? JsonPrimitive)?.doubleOrNull
                }
                if (coords.isNotEmpty()) {
                    systemFields["geolocation"] = coords
                }
            }
        }

        // Extract tags
        jsonObject["_tags"]?.let { tagsElement ->
            if (tagsElement is JsonArray) {
                val tags = tagsElement.mapNotNull {
                    (it as? JsonPrimitive)?.contentOrNull
                }
                if (tags.isNotEmpty()) {
                    systemFields["tags"] = tags
                }
            }
        }

        return if (systemFields.isNotEmpty()) {
            Json.encodeToString(
                JsonObject.serializer(),
                JsonObject(systemFields.mapValues { (_, value) ->
                    when (value) {
                        is List<*> -> JsonArray(value.map {
                            when (it) {
                                is Double -> JsonPrimitive(it)
                                is String -> JsonPrimitive(it)
                                else -> JsonPrimitive(it.toString())
                            }
                        })
                        else -> JsonPrimitive(value.toString())
                    }
                })
            )
        } else {
            null
        }
    }

    private fun JsonObject.extractString(key: String): String? {
        return this[key]?.jsonPrimitive?.contentOrNull
    }

    private fun JsonObject.extractId(key: String): String? {
        val element = this[key] ?: return null
        val primitive = element.jsonPrimitive
        return primitive.intOrNull?.toString() ?: primitive.contentOrNull
    }

    private fun parseSubmissionTime(timeString: String): Long? {
        return try {
            // Try parsing as ISO 8601 without timezone (assumes UTC)
            val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
            val localDateTime = LocalDateTime.parse(timeString, formatter)
            localDateTime.toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (e: DateTimeParseException) {
            try {
                // Try parsing as ISO 8601 with timezone
                Instant.parse(timeString).toEpochMilli()
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun formatTimestampToIso(timestamp: Long): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val localDateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC)
        return localDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }
}
