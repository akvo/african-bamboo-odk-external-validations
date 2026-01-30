package org.akvo.afribamodkvalidator.data.repository

import org.akvo.afribamodkvalidator.data.dao.FormMetadataDao
import org.akvo.afribamodkvalidator.data.dao.SubmissionDao
import org.akvo.afribamodkvalidator.data.entity.FormMetadataEntity
import org.akvo.afribamodkvalidator.data.entity.SubmissionEntity
import org.akvo.afribamodkvalidator.data.network.KoboApiService
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KoboRepository @Inject constructor(
    private val apiService: KoboApiService,
    private val submissionDao: SubmissionDao,
    private val formMetadataDao: FormMetadataDao
) {

    /**
     * Performs a delta sync, fetching only submissions newer than the last sync.
     * If no previous sync exists, falls back to full fetch.
     *
     * @return Result containing the number of new/updated submissions fetched
     */
    suspend fun resync(assetUid: String): Result<Int> {
        val lastSyncTimestamp = formMetadataDao.getLastSyncTimestamp(assetUid)

        // If no previous sync, do a full fetch
        if (lastSyncTimestamp == null) {
            return fetchSubmissions(assetUid)
        }

        return try {
            var totalFetched = 0
            var start = 0
            val pageSize = KoboApiService.DEFAULT_PAGE_SIZE

            // Build query for submissions newer than last sync
            val lastSyncIso = formatTimestampToIso(lastSyncTimestamp)
            val query = """{"_submission_time": {"${"$"}gt": "$lastSyncIso"}}"""

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
                    totalFetched += entities.size
                }

                start += pageSize
            } while (response.next != null)

            // Update sync timestamp if we fetched anything
            if (totalFetched > 0) {
                val latestSubmissionTime = submissionDao.getLatestSubmissionTime(assetUid)
                if (latestSubmissionTime != null) {
                    formMetadataDao.insertOrUpdate(
                        FormMetadataEntity(
                            assetUid = assetUid,
                            lastSyncTimestamp = latestSubmissionTime
                        )
                    )
                }
            }

            Result.success(totalFetched)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches all submissions for a form (initial download).
     *
     * @return Result containing the total number of submissions fetched
     */
    suspend fun fetchSubmissions(assetUid: String): Result<Int> {
        return try {
            var totalFetched = 0
            var start = 0
            val pageSize = KoboApiService.DEFAULT_PAGE_SIZE

            do {
                val response = apiService.getSubmissions(
                    assetUid = assetUid,
                    limit = pageSize,
                    start = start
                )

                val entities = response.results.mapNotNull { jsonObject ->
                    transformToEntity(assetUid, jsonObject)
                }

                if (entities.isNotEmpty()) {
                    submissionDao.insertAll(entities)
                    totalFetched += entities.size
                }

                start += pageSize
            } while (response.next != null)

            // Update sync timestamp
            val latestSubmissionTime = submissionDao.getLatestSubmissionTime(assetUid)
            if (latestSubmissionTime != null) {
                formMetadataDao.insertOrUpdate(
                    FormMetadataEntity(
                        assetUid = assetUid,
                        lastSyncTimestamp = latestSubmissionTime
                    )
                )
            }

            Result.success(totalFetched)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
            kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(),
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
