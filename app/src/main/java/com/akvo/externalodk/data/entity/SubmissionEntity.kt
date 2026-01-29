package com.akvo.externalodk.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Generic hybrid entity for storing submissions from ANY KoboToolbox form.
 *
 * Hybrid Storage Strategy:
 * - System Columns: Indexed, typed fields for sorting, filtering, and list display
 * - rawData JSON Blob: Stores the entire dynamic form payload for detail view parsing
 *
 * This design supports forms with completely different schemas (e.g., "manual_boundary"
 * vs "Title_Deed_First_Page") without requiring schema-specific columns.
 *
 * @param _uuid The unique identifier from KoboToolbox (primary key)
 * @param assetUid The form ID (asset UID) for filtering submissions by form
 * @param _id Internal KoboToolbox ID
 * @param submissionTime Parsed from "_submission_time" as Long for efficient sorting
 * @param submittedBy Parsed from "_submitted_by" for list display
 * @param instanceName Parsed from "meta/instanceName" for display (ODK Collect style)
 * @param rawData Full JSON string of the dynamic form data (all question responses)
 * @param systemData Optional JSON string for system fields like "_geolocation", "_tags"
 */
@Entity(
    tableName = "submissions",
    indices = [
        Index(value = ["assetUid"]),
        Index(value = ["submissionTime"]),
        Index(value = ["instanceName"]),
        Index(value = ["_uuid"])
    ]
)
data class SubmissionEntity(
    @PrimaryKey
    val _uuid: String,
    val assetUid: String,
    val _id: String,
    val submissionTime: Long,
    val submittedBy: String?,
    val instanceName: String?,
    val rawData: String,
    val systemData: String? = null
)
