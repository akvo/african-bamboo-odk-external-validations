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
import org.akvo.afribamodkvalidator.data.dao.PlotWarningDao
import org.akvo.afribamodkvalidator.data.dao.SubmissionDao
import kotlinx.serialization.encodeToString
import org.akvo.afribamodkvalidator.data.entity.FormMetadataEntity
import org.akvo.afribamodkvalidator.data.entity.PlotWarningEntity
import org.akvo.afribamodkvalidator.data.entity.SubmissionEntity
import org.akvo.afribamodkvalidator.data.network.KoboApiService
import org.akvo.afribamodkvalidator.validation.GeoValue
import org.akvo.afribamodkvalidator.validation.GeoValueParser
import org.akvo.afribamodkvalidator.validation.WarningRuleEngine
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
    private val plotExtractor: PlotExtractor,
    private val plotWarningDao: PlotWarningDao,
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

        // Step 3: Post-processing
        val polygonFields = fetchAndStorePolygonFields(assetUid)
        val dataChanged = totalFetched > 0 || reconcileRestored > 0
        if (dataChanged) {
            matchDraftsToSubmissions()
        }
        // Always run plot extraction + warning computation (catches submissions
        // that had plots before warnings feature, or after polygon field discovery changes)
        extractPlotsFromSubmissions(assetUid, polygonFields)

        // Always advance sync timestamp after successful resync.
        // Uses current time to avoid re-fetching caused by sub-second timestamp
        // truncation (Kobo API returns seconds but compares with sub-second precision).
        val storedFields = formMetadataDao.getPolygonFields(assetUid)
        formMetadataDao.insertOrUpdate(
            FormMetadataEntity(
                assetUid = assetUid,
                lastSyncTimestamp = System.currentTimeMillis(),
                polygonFields = storedFields
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
        // Discover polygon fields from asset detail before fetching submissions
        val polygonFields = fetchAndStorePolygonFields(assetUid)

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
                    lastSyncTimestamp = System.currentTimeMillis(),
                    polygonFields = Json.encodeToString(polygonFields)
                )
            )
            matchDraftsToSubmissions()
            extractPlotsFromSubmissions(assetUid, polygonFields)
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
    private suspend fun extractPlotsFromSubmissions(assetUid: String, polygonFields: List<String>) {
        val startTime = System.currentTimeMillis()
        val submissions = submissionDao.getSubmissionsSync(assetUid)

        if (submissions.isEmpty()) {
            Log.d(TAG, "extractPlotsFromSubmissions: No submissions for $assetUid")
            return
        }

        // Batch query: get all submissionUuids that already have plots
        val submissionUuids = submissions.map { it._uuid }
        val existingSubmissionUuids = plotDao.findExistingSubmissionUuids(submissionUuids).toSet()

        // Extract plots for submissions that don't have them yet
        val submissionsToProcess = submissions.filter { it._uuid !in existingSubmissionUuids }
        if (submissionsToProcess.isNotEmpty()) {
            val newPlots = submissionsToProcess.mapNotNull { submission ->
                plotExtractor.extractPlot(submission, polygonFields)
            }
            if (newPlots.isNotEmpty()) {
                plotDao.insertOrUpdateAll(newPlots)
            }
            Log.d(TAG, "extractPlotsFromSubmissions: Extracted ${newPlots.size} plots " +
                    "from ${submissionsToProcess.size} new submissions")
        }

        // Compute warnings for ALL submissions that don't already have warnings
        // (covers submissions that had plots before warnings feature or polygon field changes)
        val warningUuids = plotWarningDao.getAllPlotUuidsWithWarnings().toSet()
        val submissionsNeedingWarnings = submissions.filter { it._uuid !in warningUuids }
        if (submissionsNeedingWarnings.isNotEmpty()) {
            computeAndPersistWarnings(submissionsNeedingWarnings, polygonFields)
        }

        // Sync warnings to Kobo (fire-and-forget, failures logged)
        syncWarningsToKobo(assetUid)

        val elapsedMs = System.currentTimeMillis() - startTime
        Log.d(TAG, "extractPlotsFromSubmissions: ${submissions.size} submissions, " +
                "${submissionsToProcess.size} new plots, " +
                "${submissionsNeedingWarnings.size} needing warnings (${elapsedMs}ms)")
    }

    /**
     * Computes warning flags for submissions and persists them to the database.
     * Parses the geoshape from rawData, runs all 5 warning rules, and batch-inserts results.
     */
    private suspend fun computeAndPersistWarnings(submissions: List<SubmissionEntity>, polygonFields: List<String>) {
        val startTime = System.currentTimeMillis()
        var totalWarnings = 0

        for (submission in submissions) {
            try {
                val rawData = Json.parseToJsonElement(submission.rawData) as? JsonObject ?: continue
                val polygonData = extractPolygonData(rawData, polygonFields) ?: continue

                val geoValue = GeoValueParser.parse(polygonData)
                if (geoValue !is GeoValue.GeoShape) continue

                val coordinates = geoValue.coordinates
                val areaHectares = WarningRuleEngine.calculateAreaHectares(coordinates)
                val warnings = WarningRuleEngine.evaluate(coordinates, areaHectares)

                if (warnings.isNotEmpty()) {
                    // Delete existing warnings for this plot (handles resync)
                    plotWarningDao.deleteByPlotUuid(submission._uuid)

                    val entities = warnings.map { warning ->
                        PlotWarningEntity(
                            plotSubmissionUuid = submission._uuid,
                            warningType = warning.type.name,
                            message = warning.message,
                            shortText = warning.shortText,
                            value = warning.value
                        )
                    }
                    plotWarningDao.insertAll(entities)
                    totalWarnings += entities.size
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to compute warnings for submission: " + submission._uuid, e)
            }
        }

        val elapsedMs = System.currentTimeMillis() - startTime
        Log.d(TAG, "computeAndPersistWarnings: Generated " + totalWarnings + " warnings for " + submissions.size + " submissions in " + elapsedMs + "ms")
    }

    /**
     * Syncs warnings to Kobo using two strategies:
     * 1. Primary: PATCH dcu_validation_warnings field (one call per submission)
     * 2. Fallback: POST individual notes via v1 API
     *
     * Failures are logged but don't block the sync flow.
     * Unsynced warnings are retried on the next sync/resync cycle.
     */
    private suspend fun syncWarningsToKobo(assetUid: String) {
        syncWarningsViaField(assetUid)
        syncWarningsViaNotes()
    }

    /**
     * Primary sync: PATCH the dcu_validation_warnings field via bulk API.
     * Groups unsynced warnings by submission, builds pipe-delimited text per submission,
     * then sends one bulk PATCH per unique warning text.
     */
    private suspend fun syncWarningsViaField(assetUid: String) {
        val unsyncedWarnings = plotWarningDao.getFieldUnsyncedWarnings()
        if (unsyncedWarnings.isEmpty()) return

        // Group by submission UUID and build pipe-delimited text
        val warningsBySubmission = unsyncedWarnings.groupBy { it.plotSubmissionUuid }
        val submissionWarningTexts = mutableMapOf<String, Pair<String, String>>() // uuid -> (koboId, text)

        for ((submissionUuid, warnings) in warningsBySubmission) {
            val submission = submissionDao.getByUuid(submissionUuid) ?: continue
            val pipeDelimited = warnings.joinToString(" | ") { it.shortText }
            submissionWarningTexts[submissionUuid] = Pair(submission._id, pipeDelimited)
        }

        // Group submissions by warning text for efficient bulk calls
        val byText = submissionWarningTexts.entries.groupBy({ it.value.second }) { it }

        for ((warningText, entries) in byText) {
            try {
                val koboIds = entries.mapNotNull { it.value.first.toIntOrNull() }
                if (koboIds.isEmpty()) continue
                val payload = JsonObject(mapOf(
                    "payload" to JsonObject(mapOf(
                        "submission_ids" to JsonArray(koboIds.map { JsonPrimitive(it) }),
                        "data" to JsonObject(mapOf(
                            "dcu_validation_warnings" to JsonPrimitive(warningText)
                        ))
                    ))
                ))
                apiService.patchSubmissionsBulk(assetUid, payload)
                // Mark all submissions in this batch as synced
                for (entry in entries) {
                    plotWarningDao.markFieldSynced(entry.key)
                }
                Log.d(TAG, "syncWarningsViaField: Bulk patched ${entries.size} submissions")
            } catch (e: Exception) {
                Log.e(TAG, "syncWarningsViaField: Failed to bulk patch ${entries.size} submissions", e)
            }
        }
    }

    /**
     * Fallback sync: POST each warning as a note via the v1 API.
     */
    private suspend fun syncWarningsViaNotes() {
        val unsyncedWarnings = plotWarningDao.getNotesUnsyncedWarnings()
        if (unsyncedWarnings.isEmpty()) return

        for (warning in unsyncedWarnings) {
            try {
                val submission = submissionDao.getByUuid(warning.plotSubmissionUuid) ?: continue
                val noteText = "[DCU Warning] ${warning.message}"
                apiService.addNote(noteText, submission._id)
                plotWarningDao.markNoteSynced(warning.id)
                Log.d(TAG, "syncWarningsViaNotes: Posted note for warning ${warning.id}")
            } catch (e: Exception) {
                Log.e(TAG, "syncWarningsViaNotes: Failed to post note for warning ${warning.id}", e)
            }
        }
    }

    /**
     * Extracts polygon data string from rawData JSON using discovered field paths.
     */
    private fun extractPolygonData(rawData: JsonObject, polygonFields: List<String>): String? {
        for (field in polygonFields) {
            val value = rawData[field]?.jsonPrimitive?.contentOrNull
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    /**
     * Fetches asset detail from Kobo API and discovers geoshape/geotrace field paths.
     * Stores discovered fields in FormMetadataEntity for reuse.
     * Falls back to previously stored fields on API failure.
     */
    private suspend fun fetchAndStorePolygonFields(assetUid: String): List<String> {
        return try {
            val assetDetail = apiService.getAssetDetail(assetUid)
            val fields = extractPolygonFieldPaths(assetDetail)
            if (fields.isNotEmpty()) {
                formMetadataDao.updatePolygonFields(assetUid, Json.encodeToString(fields))
            }
            Log.d(TAG, "fetchAndStorePolygonFields: Discovered ${fields.size} polygon fields: $fields")
            fields
        } catch (e: Exception) {
            Log.e(TAG, "fetchAndStorePolygonFields: Failed to fetch asset detail, using stored fields", e)
            val stored = formMetadataDao.getPolygonFields(assetUid)
            if (stored != null) Json.decodeFromString(stored) else emptyList()
        }
    }

    /**
     * Reads previously stored polygon fields from the database.
     */
    private suspend fun getStoredPolygonFields(assetUid: String): List<String> {
        val stored = formMetadataDao.getPolygonFields(assetUid)
        return if (stored != null) Json.decodeFromString(stored) else emptyList()
    }

    /**
     * Extracts geoshape/geotrace field paths from Kobo asset detail response.
     * Parses content.survey and filters for geo field types.
     * Prefers $xpath (full path) with fallback to name.
     */
    internal fun extractPolygonFieldPaths(assetDetail: JsonObject): List<String> {
        val content = assetDetail["content"] as? JsonObject ?: return emptyList()
        val survey = content["survey"] as? JsonArray ?: return emptyList()

        return survey.filterIsInstance<JsonObject>()
            .filter { item ->
                val type = item["type"]?.jsonPrimitive?.contentOrNull ?: ""
                type == "geoshape" || type == "geotrace"
            }
            .mapNotNull { item ->
                item["\$xpath"]?.jsonPrimitive?.contentOrNull
                    ?: item["name"]?.jsonPrimitive?.contentOrNull
            }
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
        val polygonFields = getStoredPolygonFields(assetUid)
        extractPlotsFromSubmissions(assetUid, polygonFields)
    }

    private fun isRejected(jsonObject: JsonObject): Boolean {
        val validationStatus = jsonObject["_validation_status"] as? JsonObject ?: return false
        val uid = validationStatus["uid"]?.jsonPrimitive?.contentOrNull ?: return false
        return uid == VALIDATION_STATUS_NOT_APPROVED
    }

    private suspend fun removeRejectedSubmissions(rejectedUuids: List<String>) {
        if (rejectedUuids.isEmpty()) return

        val startTime = System.currentTimeMillis()
        val warningsDeleted = plotWarningDao.deleteByPlotUuids(rejectedUuids)
        val plotsDeleted = plotDao.deleteBySubmissionUuids(rejectedUuids)
        val submissionsDeleted = submissionDao.deleteByUuids(rejectedUuids)
        val elapsedMs = System.currentTimeMillis() - startTime

        Log.d(TAG, "removeRejectedSubmissions: Deleted $submissionsDeleted submissions " +
                "$plotsDeleted plots, and $warningsDeleted warnings in ${elapsedMs}ms")
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
