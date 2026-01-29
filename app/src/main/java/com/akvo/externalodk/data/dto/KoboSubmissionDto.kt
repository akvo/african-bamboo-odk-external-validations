package com.akvo.externalodk.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Generic Kobo submission DTO.
 *
 * Uses JsonObject to capture ALL fields from a submission, including
 * dynamic form-specific questions (e.g., "manual_boundary", "region", etc.).
 *
 * The unknown/ignored keys strategy allows us to handle ANY form schema
 * without updating this model.
 */
@Serializable
data class KoboSubmissionDto(
    @SerialName("_id")
    val id: String,

    @SerialName("_uuid")
    val uuid: String,

    @SerialName("_submission_time")
    val submissionTime: String,

    @SerialName("_submitted_by")
    val submittedBy: String? = null,

    @SerialName("_geolocation")
    val geolocation: List<Double>? = null,

    @SerialName("_tags")
    val tags: List<String>? = null,

    /**
     * All dynamic form fields are captured in this JsonObject.
     * Room repository will extract system fields and dump the rest
     * into the rawData JSON string column.
     */
    val data: JsonObject
)

/**
 * Kobo API data response wrapper.
 *
 * Follows KoboToolbox API v2 pagination structure.
 */
@Serializable
data class KoboDataResponse(
    @SerialName("count")
    val count: Int,

    @SerialName("next")
    val next: String? = null,

    @SerialName("previous")
    val previous: String? = null,

    @SerialName("results")
    val results: List<JsonObject>
)

/**
 * Simplified submission model for API parsing.
 *
 * This model uses @Serializable with ignoreUnknownKeys = true in the Json
 * configuration to capture all fields, including dynamic form fields.
 */
@Serializable
data class KoboSubmissionRaw(
    @SerialName("_id")
    val id: String,

    @SerialName("_uuid")
    val uuid: String,

    @SerialName("_submission_time")
    val submissionTime: String,

    @SerialName("_submitted_by")
    val submittedBy: String? = null
)

/**
 * JSON element wrapper for storing raw submission data.
 */
@Serializable
data class KoboRawData(
    val raw: JsonElement
)
