package org.akvo.afribamodkvalidator.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for storing plot warning flags.
 *
 * Separate table (not JSON on PlotEntity) because:
 * - Queryable: filter/count plots by warning type
 * - Track sync status per channel (fieldSynced, notesSynced)
 * - Extensible without migration
 */
@Entity(
    tableName = "plot_warnings",
    indices = [
        Index(value = ["plotSubmissionUuid"]),
        Index(value = ["warningType"])
    ]
)
data class PlotWarningEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** UUID of the submission this warning belongs to */
    val plotSubmissionUuid: String,

    /** Warning type from WarningType enum (stored as string) */
    val warningType: String,

    /** Human-readable warning message for UI display and _notes API */
    val message: String,

    /** Compact machine-parseable text for dcu_validation_warnings field */
    val shortText: String,

    /** The actual measured value that triggered the warning */
    val value: Double,

    /** Whether the warning has been synced to the dcu_validation_warnings field via PATCH */
    val fieldSynced: Boolean = false,

    /** Whether the warning has been synced to _notes API via POST */
    val notesSynced: Boolean = false
)
