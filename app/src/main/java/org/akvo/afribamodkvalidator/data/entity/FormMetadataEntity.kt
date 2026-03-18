package org.akvo.afribamodkvalidator.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Metadata entity for tracking sync state and form structure per form.
 *
 * @param assetUid The form ID (asset UID) - serves as primary key
 * @param lastSyncTimestamp Timestamp of the last successful data fetch
 * @param polygonFields JSON array of geoshape/geotrace field paths discovered from asset detail.
 *   Null means "not yet fetched". Example: ["boundary_mapping/manual_boundary", "validate_polygon"]
 */
@Entity(tableName = "form_metadata")
data class FormMetadataEntity(
    @PrimaryKey
    val assetUid: String,
    val lastSyncTimestamp: Long,
    val polygonFields: String? = null
)
