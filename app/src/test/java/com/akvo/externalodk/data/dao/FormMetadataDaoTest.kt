package com.akvo.externalodk.data.dao

import com.akvo.externalodk.data.database.DatabaseTest
import com.akvo.externalodk.data.entity.FormMetadataEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormMetadataDaoTest : DatabaseTest() {

    private val formMetadataDao: FormMetadataDao by lazy { database.formMetadataDao() }

    private val testMetadata1 = FormMetadataEntity(
        assetUid = "123",
        lastSyncTimestamp = 1710477953000L // 2024-03-15
    )

    private val testMetadata2 = FormMetadataEntity(
        assetUid = "456",
        lastSyncTimestamp = 1711377918000L // 2024-03-25
    )

    @Test
    fun `insertOrUpdate should insert new metadata`() = runTest {
        // When
        formMetadataDao.insertOrUpdate(testMetadata1)

        // Then
        val retrieved = formMetadataDao.getByAssetUid("123")
        assertNotNull(retrieved)
        assertEquals(testMetadata1.assetUid, retrieved?.assetUid)
        assertEquals(testMetadata1.lastSyncTimestamp, retrieved?.lastSyncTimestamp)
    }

    @Test
    fun `insertOrUpdate should update existing metadata`() = runTest {
        // Given - insert initial metadata
        formMetadataDao.insertOrUpdate(testMetadata1)
        val initial = formMetadataDao.getByAssetUid("123")
        assertEquals(1710477953000L, initial?.lastSyncTimestamp)

        // When - insert with same assetUid but newer timestamp
        val updated = FormMetadataEntity(
            assetUid = "123",
            lastSyncTimestamp = 1711464318000L // Newer timestamp
        )
        formMetadataDao.insertOrUpdate(updated)

        // Then - should be updated (not duplicated)
        val retrieved = formMetadataDao.getByAssetUid("123")
        assertNotNull(retrieved)
        assertEquals("123", retrieved?.assetUid)
        assertEquals(1711464318000L, retrieved?.lastSyncTimestamp)
    }

    @Test
    fun `getByAssetUid should return correct metadata`() = runTest {
        // Given
        formMetadataDao.insertOrUpdate(testMetadata1)
        formMetadataDao.insertOrUpdate(testMetadata2)

        // When
        val retrieved1 = formMetadataDao.getByAssetUid("123")
        val retrieved2 = formMetadataDao.getByAssetUid("456")

        // Then
        assertNotNull(retrieved1)
        assertEquals("123", retrieved1?.assetUid)
        assertEquals(1710477953000L, retrieved1?.lastSyncTimestamp)

        assertNotNull(retrieved2)
        assertEquals("456", retrieved2?.assetUid)
        assertEquals(1711377918000L, retrieved2?.lastSyncTimestamp)
    }

    @Test
    fun `getByAssetUid should return null for non-existent asset`() = runTest {
        // When
        val retrieved = formMetadataDao.getByAssetUid("non-existent")

        // Then
        assertNull(retrieved)
    }

    @Test
    fun `getLastSyncTimestamp should return timestamp or null`() = runTest {
        // Given - empty
        assertNull(formMetadataDao.getLastSyncTimestamp("123"))

        // When - insert metadata
        formMetadataDao.insertOrUpdate(testMetadata1)

        // Then
        val timestamp = formMetadataDao.getLastSyncTimestamp("123")
        assertEquals(1710477953000L, timestamp)
    }

    @Test
    fun `deleteByAssetUid should delete only specified asset`() = runTest {
        // Given
        formMetadataDao.insertOrUpdate(testMetadata1)
        formMetadataDao.insertOrUpdate(testMetadata2)

        // When
        val deletedCount = formMetadataDao.deleteByAssetUid("123")

        // Then
        assertEquals(1, deletedCount)
        assertNull(formMetadataDao.getByAssetUid("123"))
        assertNotNull(formMetadataDao.getByAssetUid("456"))
    }

    @Test
    fun `deleteAll should clear all metadata`() = runTest {
        // Given
        formMetadataDao.insertOrUpdate(testMetadata1)
        formMetadataDao.insertOrUpdate(testMetadata2)

        // When
        val deletedCount = formMetadataDao.deleteAll()

        // Then
        assertEquals(2, deletedCount)
        assertNull(formMetadataDao.getByAssetUid("123"))
        assertNull(formMetadataDao.getByAssetUid("456"))
    }

    @Test
    fun `should track sync state per form independently`() = runTest {
        // Given - two forms with different sync times
        formMetadataDao.insertOrUpdate(
            FormMetadataEntity(assetUid = "123", lastSyncTimestamp = 1000L)
        )
        formMetadataDao.insertOrUpdate(
            FormMetadataEntity(assetUid = "456", lastSyncTimestamp = 5000L)
        )

        // When - update only form 123
        formMetadataDao.insertOrUpdate(
            FormMetadataEntity(assetUid = "123", lastSyncTimestamp = 2000L)
        )

        // Then - form 123 updated, form 456 unchanged
        assertEquals(2000L, formMetadataDao.getLastSyncTimestamp("123"))
        assertEquals(5000L, formMetadataDao.getLastSyncTimestamp("456"))
    }
}
