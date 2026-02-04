package org.akvo.afribamodkvalidator.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.akvo.afribamodkvalidator.data.entity.SubmissionEntity

/**
 * Data Access Object for submissions.
 *
 * All queries operate on system fields only. Dynamic form data is accessed
 * via the rawData JSON string in the entity.
 */
@Dao
interface SubmissionDao {

    /**
     * Insert or replace submissions.
     * Uses REPLACE strategy to handle both new inserts and updates (resync).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(submissions: List<SubmissionEntity>)

    /**
     * Insert or replace a single submission.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(submission: SubmissionEntity)

    /**
     * Get all submissions for a specific form, ordered by submission time (newest first).
     * Returns Flow for reactive UI updates.
     */
    @Query("SELECT * FROM submissions WHERE assetUid = :uid ORDER BY submissionTime DESC")
    fun getSubmissions(uid: String): Flow<List<SubmissionEntity>>

    /**
     * Get submission count for a specific form.
     */
    @Query("SELECT COUNT(*) FROM submissions WHERE assetUid = :uid")
    suspend fun getCount(uid: String): Int

    /**
     * Get the latest submission time for a specific form.
     * Used for incremental sync (fetch only data newer than last sync).
     */
    @Query("SELECT MAX(submissionTime) FROM submissions WHERE assetUid = :uid")
    suspend fun getLatestSubmissionTime(uid: String): Long?

    /**
     * Get all submissions (sync operation, not Flow).
     */
    @Query("SELECT * FROM submissions WHERE assetUid = :uid ORDER BY submissionTime DESC")
    suspend fun getSubmissionsSync(uid: String): List<SubmissionEntity>

    /**
     * Delete all submissions for a specific form.
     */
    @Query("DELETE FROM submissions WHERE assetUid = :uid")
    suspend fun deleteByAssetUid(uid: String): Int

    /**
     * Get a single submission by UUID.
     */
    @Query("SELECT * FROM submissions WHERE _uuid = :uuid")
    suspend fun getByUuid(uuid: String): SubmissionEntity?

    /**
     * Find submission by instanceName for draft matching.
     * Returns the first match (should be unique in practice).
     */
    @Query("SELECT * FROM submissions WHERE instanceName = :instanceName LIMIT 1")
    suspend fun findByInstanceName(instanceName: String): SubmissionEntity?

    /**
     * Delete all submissions (for logout/data clearing).
     */
    @Query("DELETE FROM submissions")
    suspend fun deleteAll(): Int
}
