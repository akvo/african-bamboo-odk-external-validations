package org.akvo.afribamodkvalidator.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity for storing plot data with bounding box columns for spatial queries.
 *
 * Design decisions:
 * - polygonWkt: Stores polygon in WKT format for JTS geometry operations
 * - minLat/maxLat/minLon/maxLon: Bounding box for efficient SQL pre-filtering
 * - isDraft: Tracks whether plot is from draft (local) or synced submission
 * - region: Indexed for proximity filtering (only check overlaps within same region)
 *
 * Index strategy for findOverlapCandidates query:
 * - Composite (region, minLon) supports equality on region + range on minLon
 * - Composite (region, minLat) supports equality on region + range on minLat
 * - SQLite query planner selects the most effective index per query
 */
@Entity(
    tableName = "plots",
    indices = [
        Index(value = ["region", "minLon"]),
        Index(value = ["region", "minLat"]),
        Index(value = ["instanceName"]),
        Index(value = ["submissionUuid"])
    ]
)
data class PlotEntity(
    @PrimaryKey
    val uuid: String,

    /** Farmer full name (First_Name + Father_s_Name + Grandfather_s_Name) */
    val plotName: String,

    /** For matching to existing draft submissions */
    val instanceName: String,

    /** Polygon in WKT (Well-Known Text) format for JTS geometry operations */
    val polygonWkt: String,

    /** Bounding box - minimum latitude */
    val minLat: Double,

    /** Bounding box - maximum latitude */
    val maxLat: Double,

    /** Bounding box - minimum longitude */
    val minLon: Double,

    /** Bounding box - maximum longitude */
    val maxLon: Double,

    /** True if from local draft, false if from synced submission */
    val isDraft: Boolean = true,

    /** KoboToolbox form asset UID */
    val formId: String,

    /** Administrative region for proximity filtering (e.g., woreda) */
    val region: String,

    /** Administrative sub-region (e.g., kebele) */
    val subRegion: String,

    /** Timestamp when plot record was created */
    val createdAt: Long = System.currentTimeMillis(),

    /** UUID of the synced submission (null for drafts) */
    val submissionUuid: String? = null
)
