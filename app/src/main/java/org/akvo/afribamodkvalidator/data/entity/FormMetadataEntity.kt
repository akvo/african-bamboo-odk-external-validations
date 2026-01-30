package org.akvo.afribamodkvalidator.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Metadata entity for tracking sync state per form.
 *
 * Stores the last successful sync timestamp for each form (asset),
 * enabling incremental/delta sync operations.
 *
 * @param assetUid The form ID (asset UID) - serves as primary key
 * @param lastSyncTimestamp Timestamp of the last successful data fetch
 */
@Entity(tableName = "form_metadata")
data class FormMetadataEntity(
    @PrimaryKey
    val assetUid: String,
    val lastSyncTimestamp: Long
)
