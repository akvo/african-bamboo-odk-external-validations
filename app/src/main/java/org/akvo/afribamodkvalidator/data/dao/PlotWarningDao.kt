package org.akvo.afribamodkvalidator.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.akvo.afribamodkvalidator.data.entity.PlotWarningEntity

/**
 * Data Access Object for plot warning operations.
 *
 * Supports CRUD, batch operations, sync status tracking,
 * and aggregation queries for UI display.
 */
@Dao
interface PlotWarningDao {

    /**
     * Insert a batch of warnings for a plot.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(warnings: List<PlotWarningEntity>)

    /**
     * Get all warnings for a specific plot by submission UUID.
     */
    @Query("SELECT * FROM plot_warnings WHERE plotSubmissionUuid = :submissionUuid")
    suspend fun getWarningsForPlot(submissionUuid: String): List<PlotWarningEntity>

    /**
     * Get warning counts grouped by plot submission UUID for a specific form.
     * Returns a list of pairs (submissionUuid, count) for plots that have warnings.
     * Used for displaying warning badges on submission list items.
     */
    @Query("""
        SELECT pw.plotSubmissionUuid, COUNT(*) as warningCount
        FROM plot_warnings pw
        INNER JOIN submissions s ON pw.plotSubmissionUuid = s._uuid
        WHERE s.assetUid = :formId
        GROUP BY pw.plotSubmissionUuid
    """)
    fun getWarningCountsByForm(formId: String): Flow<List<WarningCount>>

    /**
     * Delete all warnings for a specific plot (for resync cleanup).
     */
    @Query("DELETE FROM plot_warnings WHERE plotSubmissionUuid = :submissionUuid")
    suspend fun deleteByPlotUuid(submissionUuid: String): Int

    /**
     * Delete warnings for multiple plots (batch cleanup).
     */
    @Query("DELETE FROM plot_warnings WHERE plotSubmissionUuid IN (:submissionUuids)")
    suspend fun deleteByPlotUuids(submissionUuids: List<String>): Int

    /**
     * Get all warnings that haven't been synced to the dcu_validation_warnings field.
     */
    @Query("SELECT * FROM plot_warnings WHERE fieldSynced = 0")
    suspend fun getFieldUnsyncedWarnings(): List<PlotWarningEntity>

    /**
     * Get all warnings that haven't been synced to _notes API.
     */
    @Query("SELECT * FROM plot_warnings WHERE notesSynced = 0")
    suspend fun getNotesUnsyncedWarnings(): List<PlotWarningEntity>

    /**
     * Mark warnings as synced to the field for a specific plot.
     */
    @Query("UPDATE plot_warnings SET fieldSynced = 1 WHERE plotSubmissionUuid = :submissionUuid")
    suspend fun markFieldSynced(submissionUuid: String): Int

    /**
     * Mark a specific warning as synced to notes.
     */
    @Query("UPDATE plot_warnings SET notesSynced = 1 WHERE id = :warningId")
    suspend fun markNoteSynced(warningId: Long): Int

    /**
     * Get all submission UUIDs that already have warnings.
     * Used to avoid recomputing warnings for already-processed submissions.
     */
    @Query("SELECT DISTINCT plotSubmissionUuid FROM plot_warnings")
    suspend fun getAllPlotUuidsWithWarnings(): List<String>

    /**
     * Delete all warnings (for logout/data clearing).
     */
    @Query("DELETE FROM plot_warnings")
    suspend fun deleteAll(): Int
}

/**
 * Projection class for warning count aggregation query.
 */
data class WarningCount(
    val plotSubmissionUuid: String,
    val warningCount: Int
)
