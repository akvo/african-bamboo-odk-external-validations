package com.akvo.externalodk.data.dao

import app.cash.turbine.test
import com.akvo.externalodk.data.database.DatabaseTest
import com.akvo.externalodk.data.dto.KoboDataResponse
import com.akvo.externalodk.data.entity.SubmissionEntity
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Unit tests for SubmissionDao using actual KoboToolbox API response fixtures.
 *
 * Fixtures are located in src/test/resources/fixtures/:
 * - assets-123-data-list.json: Form 123 data with Title_Deed and boundary_mapping fields
 * - assets-456-data-list.json: Form 456 data with counter and manual_boundary fields
 */
class SubmissionDaoTest : DatabaseTest() {

    private val submissionDao: SubmissionDao by lazy { database.submissionDao() }
    private val json = Json { ignoreUnknownKeys = true }

    // Asset UIDs matching fixture filenames
    private val assetUid123 = "123"
    private val assetUid456 = "456"

    // Parse submission time from Kobo format: "2024-03-15T07:45:53"
    private fun parseSubmissionTime(timeString: String): Long {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        return format.parse(timeString)?.time ?: 0L
    }

    /**
     * Load fixture from resources and parse as KoboDataResponse
     */
    private fun loadFixture(assetUid: String): KoboDataResponse {
        val fileName = "assets-$assetUid-data-list.json"
        val fixtureContent = this::class.java.classLoader?.getResourceAsStream("fixtures/$fileName")
            ?.bufferedReader()?.readText()
            ?: throw IllegalArgumentException("Fixture not found: $fileName")

        return json.decodeFromString<KoboDataResponse>(fixtureContent)
    }

    /**
     * Convert Kobo JSON result to SubmissionEntity
     */
    private fun resultToEntity(result: JsonObject, assetUid: String): SubmissionEntity {
        val uuid = result["_uuid"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing _uuid")
        val id = result["_id"]?.toString()
            ?: throw IllegalArgumentException("Missing _id")
        val submissionTime = result["_submission_time"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing _submission_time")
        val submittedBy = result["_submitted_by"]?.jsonPrimitive?.content
        val instanceName = result["meta/instanceName"]?.jsonPrimitive?.content

        // Convert the entire JsonObject back to string for rawData
        val rawJson = json.encodeToString(JsonElement.serializer(), result)

        return SubmissionEntity(
            _uuid = uuid,
            assetUid = assetUid,
            _id = id,
            submissionTime = parseSubmissionTime(submissionTime),
            submittedBy = submittedBy,
            instanceName = instanceName,
            rawData = rawJson,
            systemData = null
        )
    }

    @Test
    fun `insertAll should insert submissions from fixture 123`() = runTest {
        // Given - load fixture
        val response = loadFixture(assetUid123)
        val entities = response.results.map { result ->
            resultToEntity(result, assetUid123)
        }

        // When - insert all
        submissionDao.insertAll(entities)

        // Then
        val count = submissionDao.getCount(assetUid123)
        assertEquals(1, count) // Fixture 123 has 1 result
    }

    @Test
    fun `insertAll should insert submissions from fixture 456`() = runTest {
        // Given - load fixture
        val response = loadFixture(assetUid456)
        val entities = response.results.map { result ->
            resultToEntity(result, assetUid456)
        }

        // When - insert all
        submissionDao.insertAll(entities)

        // Then
        val count = submissionDao.getCount(assetUid456)
        assertEquals(3, count) // Fixture 456 has 3 results
    }

    @Test
    fun `getSubmissions should return Flow ordered by submissionTime DESC`() = runTest {
        // Given - load fixture 456 (has 3 submissions)
        val response = loadFixture(assetUid456)
        val entities = response.results.map { result ->
            resultToEntity(result, assetUid456)
        }
        submissionDao.insertAll(entities)

        // When & Then
        submissionDao.getSubmissions(assetUid456).test {
            val submissions = awaitItem()
            assertEquals(3, submissions.size)

            // Verify newest first (March 25 > Feb 20 > Jan 15)
            assertEquals("fedcba98-7654-3210-fedc-ba9876543210", submissions[0]._uuid) // 2024-03-25
            assertEquals("abcdef12-3456-7890-abcd-ef1234567890", submissions[1]._uuid) // 2024-02-20
            assertEquals("12345678-abcd-1234-efgh-123456789abc", submissions[2]._uuid) // 2024-01-15

            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `getSubmissions Flow should emit new values on insert`() = runTest {
        // Given - empty state
        submissionDao.getSubmissions(assetUid123).test {
            // First emission should be empty
            var submissions = awaitItem()
            assertTrue(submissions.isEmpty())

            // When - load and insert from fixture
            val response = loadFixture(assetUid123)
            val entity = resultToEntity(response.results[0], assetUid123)
            submissionDao.insert(entity)

            // Then - should emit updated list
            submissions = awaitItem()
            assertEquals(1, submissions.size)
            assertEquals("12345678-90ab-cdef-1234-567890abcdef", submissions[0]._uuid)

            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `getByUuid should retrieve correct submission with dynamic fields`() = runTest {
        // Given - insert from fixture 123 (has complex nested fields)
        val response = loadFixture(assetUid123)
        val entity = resultToEntity(response.results[0], assetUid123)
        submissionDao.insert(entity)

        // When
        val retrieved = submissionDao.getByUuid("12345678-90ab-cdef-1234-567890abcdef")

        // Then - verify system fields
        assertNotNull(retrieved)
        assertEquals("12345678-90ab-cdef-1234-567890abcdef", retrieved?._uuid)
        assertEquals(assetUid123, retrieved?.assetUid)
        assertEquals("testuser123", retrieved?.submittedBy)

        // Verify rawData contains dynamic form fields
        val rawData = retrieved?.rawData ?: ""
        assertTrue(rawData.contains("region"))
        assertTrue(rawData.contains("NOR"))
        assertTrue(rawData.contains("woreda"))
        assertTrue(rawData.contains("boundary_mapping"))
    }

    @Test
    fun `getByUuid should return null for non-existent UUID`() = runTest {
        // When
        val retrieved = submissionDao.getByUuid("non-existent-uuid")

        // Then
        assertNull(retrieved)
    }

    @Test
    fun `insert should replace existing submission on same UUID`() = runTest {
        // Given - insert from fixture
        val response = loadFixture(assetUid123)
        var entity = resultToEntity(response.results[0], assetUid123)
        submissionDao.insert(entity)

        var count = submissionDao.getCount(assetUid123)
        assertEquals(1, count)

        // When - insert same UUID with different data
        entity = entity.copy(
            submittedBy = "updated_user",
            rawData = """{"updated":"data"}"""
        )
        submissionDao.insert(entity)

        // Then - should still be 1 (replaced, not added)
        count = submissionDao.getCount(assetUid123)
        assertEquals(1, count)

        // Verify the data was updated
        val retrieved = submissionDao.getByUuid(entity._uuid)
        assertEquals("updated_user", retrieved?.submittedBy)
        assertEquals("""{"updated":"data"}""", retrieved?.rawData)
    }

    @Test
    fun `deleteByAssetUid should only delete for specified asset`() = runTest {
        // Given - insert from both fixtures
        val response123 = loadFixture(assetUid123)
        val response456 = loadFixture(assetUid456)

        val entities123 = response123.results.map { resultToEntity(it, assetUid123) }
        val entities456 = response456.results.map { resultToEntity(it, assetUid456) }

        submissionDao.insertAll(entities123)
        submissionDao.insertAll(entities456)

        assertEquals(1, submissionDao.getCount(assetUid123))
        assertEquals(3, submissionDao.getCount(assetUid456))

        // When - delete only asset 123
        val deletedCount = submissionDao.deleteByAssetUid(assetUid123)

        // Then
        assertEquals(1, deletedCount)
        assertEquals(0, submissionDao.getCount(assetUid123))
        assertEquals(3, submissionDao.getCount(assetUid456)) // 456 unaffected
    }

    @Test
    fun `getLatestSubmissionTime should return max timestamp from fixtures`() = runTest {
        // Given - load fixture 456 (has multiple dates)
        val response = loadFixture(assetUid456)
        val entities = response.results.map { result ->
            resultToEntity(result, assetUid456)
        }
        submissionDao.insertAll(entities)

        // When
        val latest = submissionDao.getLatestSubmissionTime(assetUid456)

        // Then - should be March 25, 2024 (newest in fixture)
        assertNotNull(latest)
        val expectedLatest = parseSubmissionTime("2024-03-25T16:45:18")
        assertEquals(expectedLatest, latest)
    }

    @Test
    fun `rawData preserves all dynamic form fields`() = runTest {
        // Given - fixture 123 has many dynamic fields
        val response = loadFixture(assetUid123)
        val entity = resultToEntity(response.results[0], assetUid123)
        submissionDao.insert(entity)

        // When
        val retrieved = submissionDao.getByUuid(entity._uuid)

        // Then - verify various dynamic fields are preserved
        val rawData = retrieved?.rawData ?: ""

        // Form-specific fields from fixture
        assertTrue(rawData.contains("region"))
        assertTrue(rawData.contains("NOR"))
        assertTrue(rawData.contains("woreda"))
        assertTrue(rawData.contains("NOR-05"))
        assertTrue(rawData.contains("kebele"))
        assertTrue(rawData.contains("First_Name"))
        assertTrue(rawData.contains("John"))
        assertTrue(rawData.contains("Father_s_Name"))
        assertTrue(rawData.contains("boundary_mapping"))
        assertTrue(rawData.contains("Title_Deed_First_Page"))
    }

    @Test
    fun `deleteAll should clear all submissions across all assets`() = runTest {
        // Given - insert from both fixtures
        val response123 = loadFixture(assetUid123)
        val response456 = loadFixture(assetUid456)

        submissionDao.insertAll(response123.results.map { resultToEntity(it, assetUid123) })
        submissionDao.insertAll(response456.results.map { resultToEntity(it, assetUid456) })

        assertEquals(1, submissionDao.getCount(assetUid123))
        assertEquals(3, submissionDao.getCount(assetUid456))

        // When
        val deletedCount = submissionDao.deleteAll()

        // Then
        assertEquals(4, deletedCount)
        assertEquals(0, submissionDao.getCount(assetUid123))
        assertEquals(0, submissionDao.getCount(assetUid456))
    }
}
