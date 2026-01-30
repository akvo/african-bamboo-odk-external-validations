package org.akvo.afribamodkvalidator.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.akvo.afribamodkvalidator.data.entity.FormMetadataEntity

/**
 * Data Access Object for form metadata.
 *
 * Manages sync state tracking per form.
 */
@Dao
interface FormMetadataDao {

    /**
     * Insert or update form metadata.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(metadata: FormMetadataEntity)

    /**
     * Get metadata for a specific form.
     */
    @Query("SELECT * FROM form_metadata WHERE assetUid = :uid")
    suspend fun getByAssetUid(uid: String): FormMetadataEntity?

    /**
     * Get last sync timestamp for a specific form.
     */
    @Query("SELECT lastSyncTimestamp FROM form_metadata WHERE assetUid = :uid")
    suspend fun getLastSyncTimestamp(uid: String): Long?

    /**
     * Delete metadata for a specific form.
     */
    @Query("DELETE FROM form_metadata WHERE assetUid = :uid")
    suspend fun deleteByAssetUid(uid: String): Int

    /**
     * Delete all metadata (for logout/data clearing).
     */
    @Query("DELETE FROM form_metadata")
    suspend fun deleteAll(): Int
}
