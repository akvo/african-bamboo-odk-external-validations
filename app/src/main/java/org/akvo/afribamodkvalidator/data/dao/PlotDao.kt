package org.akvo.afribamodkvalidator.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.akvo.afribamodkvalidator.data.entity.PlotEntity

/**
 * Data Access Object for plot operations.
 *
 * Supports overlap detection via bounding box pre-filtering and draft/submission matching.
 */
@Dao
interface PlotDao {

    /**
     * Insert or replace a plot.
     * Uses REPLACE strategy to handle both new inserts and updates.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(plot: PlotEntity)

    /**
     * Insert or replace multiple plots.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(plots: List<PlotEntity>)

    /**
     * Find overlap candidates using region and bounding box pre-filter.
     *
     * Returns plots in the same region whose bounding boxes intersect with the query bbox.
     * Excludes the specified UUID (for self-comparison when updating).
     *
     * Bbox intersection logic: Two boxes intersect if they overlap on both axes.
     * - X-axis overlap: existingMinLon <= queryMaxLon AND existingMaxLon >= queryMinLon
     * - Y-axis overlap: existingMinLat <= queryMaxLat AND existingMaxLat >= queryMinLat
     */
    @Query("""
        SELECT * FROM plots
        WHERE region = :region
        AND uuid != :excludeUuid
        AND minLon <= :maxLon AND maxLon >= :minLon
        AND minLat <= :maxLat AND maxLat >= :minLat
    """)
    suspend fun findOverlapCandidates(
        region: String,
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        excludeUuid: String
    ): List<PlotEntity>

    /**
     * Find a plot by instanceName (for draft matching).
     */
    @Query("SELECT * FROM plots WHERE instanceName = :instanceName LIMIT 1")
    suspend fun findByInstanceName(instanceName: String): PlotEntity?

    /**
     * Update draft status when a submission is synced.
     * Links the draft to the submission and marks it as no longer a draft.
     * Only updates records that are still drafts to prevent overwriting already-synced plots.
     */
    @Query("""
        UPDATE plots
        SET isDraft = 0, submissionUuid = :submissionUuid
        WHERE instanceName = :instanceName
        AND isDraft = 1
    """)
    suspend fun updateDraftStatus(instanceName: String, submissionUuid: String): Int

    /**
     * Get all plots.
     */
    @Query("SELECT * FROM plots ORDER BY createdAt DESC")
    suspend fun getAll(): List<PlotEntity>

    /**
     * Get all plots as Flow for reactive UI updates.
     */
    @Query("SELECT * FROM plots ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<PlotEntity>>

    /**
     * Get all plots for a specific form.
     */
    @Query("SELECT * FROM plots WHERE formId = :formId ORDER BY createdAt DESC")
    suspend fun getByFormId(formId: String): List<PlotEntity>

    /**
     * Get a single plot by UUID.
     */
    @Query("SELECT * FROM plots WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): PlotEntity?

    /**
     * Get multiple plots by their UUIDs.
     */
    @Query("SELECT * FROM plots WHERE uuid IN (:uuids)")
    suspend fun getPlotsByUuids(uuids: List<String>): List<PlotEntity>

    /**
     * Delete a plot by UUID.
     */
    @Query("DELETE FROM plots WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: String): Int

    /**
     * Delete all plots (for logout/data clearing).
     */
    @Query("DELETE FROM plots")
    suspend fun deleteAll(): Int
}
