package org.akvo.afribamodkvalidator.data.repository

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.akvo.afribamodkvalidator.data.dao.FormMetadataDao
import org.akvo.afribamodkvalidator.data.dao.PlotDao
import org.akvo.afribamodkvalidator.data.dao.SubmissionDao
import org.akvo.afribamodkvalidator.data.dto.KoboDataResponse
import org.akvo.afribamodkvalidator.data.entity.PlotEntity
import org.akvo.afribamodkvalidator.data.entity.SubmissionEntity
import org.akvo.afribamodkvalidator.data.network.KoboApiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for KoboRepository sync operations.
 *
 * Tests focus on:
 * - matchDraftsToSubmissions: draft-to-submission linking logic
 * - extractPlotsFromSubmissions: plot extraction from synced submissions
 *
 * Edge cases covered:
 * - Multiple drafts with same instanceName
 * - Submissions without instanceName
 * - Partial failures during bulk processing
 * - Submissions with invalid/incomplete polygon data
 */
class KoboRepositorySyncTest {

    private lateinit var repository: KoboRepository
    private lateinit var apiService: KoboApiService
    private lateinit var submissionDao: SubmissionDao
    private lateinit var formMetadataDao: FormMetadataDao
    private lateinit var plotDao: PlotDao
    private lateinit var plotExtractor: PlotExtractor

    private val testAssetUid = "test-asset-123"

    @Before
    fun setup() {
        apiService = mockk()
        submissionDao = mockk()
        formMetadataDao = mockk()
        plotDao = mockk()
        plotExtractor = mockk()

        repository = KoboRepository(
            apiService = apiService,
            submissionDao = submissionDao,
            formMetadataDao = formMetadataDao,
            plotDao = plotDao,
            plotExtractor = plotExtractor
        )
    }

    // ==================== matchDraftsToSubmissions Tests ====================

    @Test
    fun `fetchSubmissions should match drafts to submissions by instanceName`() = runTest {
        // Given - API returns submissions
        val submissions = listOf(
            createSubmission("uuid-1", "instance-A"),
            createSubmission("uuid-2", "instance-B")
        )
        setupApiToReturnSubmissions(submissions)

        // Drafts waiting to be matched
        val drafts = listOf(
            createDraftPlot("draft-1", "instance-A"),
            createDraftPlot("draft-2", "instance-B")
        )
        coEvery { plotDao.getAllDrafts() } returns drafts

        // Batch query returns matching submissions
        coEvery { submissionDao.findByInstanceNames(any()) } returns submissions

        // Setup mocks
        coEvery { submissionDao.insertAll(any()) } just Runs
        coEvery { submissionDao.getLatestSubmissionTime(any()) } returns System.currentTimeMillis()
        coEvery { formMetadataDao.insertOrUpdate(any()) } just Runs
        coEvery { formMetadataDao.getLastSyncTimestamp(any()) } returns null
        coEvery { submissionDao.getSubmissionsSync(testAssetUid) } returns submissions
        coEvery { plotDao.updateDraftStatus(any(), any()) } returns 1
        coEvery { plotDao.findExistingSubmissionUuids(any()) } returns emptyList()
        coEvery { plotExtractor.extractPlot(any()) } returns null

        // When
        repository.fetchSubmissions(testAssetUid)

        // Then - verify both drafts were matched
        coVerify { plotDao.updateDraftStatus("instance-A", "uuid-1") }
        coVerify { plotDao.updateDraftStatus("instance-B", "uuid-2") }
    }

    @Test
    fun `fetchSubmissions should handle multiple drafts with same instanceName`() = runTest {
        // Given - API returns one submission
        val submissions = listOf(createSubmission("uuid-1", "instance-A"))
        setupApiToReturnSubmissions(submissions)

        // Multiple drafts with same instanceName (should not happen in practice but test robustness)
        val drafts = listOf(
            createDraftPlot("draft-1", "instance-A"),
            createDraftPlot("draft-2", "instance-A")  // Duplicate instanceName
        )
        coEvery { plotDao.getAllDrafts() } returns drafts

        // Batch query returns the single matching submission
        coEvery { submissionDao.findByInstanceNames(any()) } returns submissions

        coEvery { submissionDao.insertAll(any()) } just Runs
        coEvery { submissionDao.getLatestSubmissionTime(any()) } returns System.currentTimeMillis()
        coEvery { formMetadataDao.insertOrUpdate(any()) } just Runs
        coEvery { formMetadataDao.getLastSyncTimestamp(any()) } returns null
        coEvery { submissionDao.getSubmissionsSync(testAssetUid) } returns submissions
        coEvery { plotDao.updateDraftStatus(any(), any()) } returns 1
        coEvery { plotDao.findExistingSubmissionUuids(any()) } returns emptyList()
        coEvery { plotExtractor.extractPlot(any()) } returns null

        // When
        repository.fetchSubmissions(testAssetUid)

        // Then - updateDraftStatus called twice (once per draft), both will match the same submission
        // The updateDraftStatus query uses instanceName, so both drafts with "instance-A" will be updated
        coVerify(exactly = 2) { plotDao.updateDraftStatus("instance-A", "uuid-1") }
    }

    @Test
    fun `fetchSubmissions should skip drafts when submission has no instanceName`() = runTest {
        // Given - API returns submission without instanceName
        val submissions = listOf(createSubmission("uuid-1", null))
        setupApiToReturnSubmissions(submissions)

        // Draft waiting for match
        val drafts = listOf(createDraftPlot("draft-1", "instance-A"))
        coEvery { plotDao.getAllDrafts() } returns drafts

        // Batch query finds no matches (submission has null instanceName)
        coEvery { submissionDao.findByInstanceNames(any()) } returns emptyList()

        coEvery { submissionDao.insertAll(any()) } just Runs
        coEvery { submissionDao.getLatestSubmissionTime(any()) } returns System.currentTimeMillis()
        coEvery { formMetadataDao.insertOrUpdate(any()) } just Runs
        coEvery { formMetadataDao.getLastSyncTimestamp(any()) } returns null
        coEvery { submissionDao.getSubmissionsSync(testAssetUid) } returns submissions
        coEvery { plotDao.findExistingSubmissionUuids(any()) } returns emptyList()
        coEvery { plotExtractor.extractPlot(any()) } returns null

        // When
        repository.fetchSubmissions(testAssetUid)

        // Then - no draft updates (no match found)
        coVerify(exactly = 0) { plotDao.updateDraftStatus(any(), any()) }
    }

    @Test
    fun `fetchSubmissions should handle no drafts gracefully`() = runTest {
        // Given - API returns submissions
        val submissions = listOf(createSubmission("uuid-1", "instance-A"))
        setupApiToReturnSubmissions(submissions)

        // No drafts to match
        coEvery { plotDao.getAllDrafts() } returns emptyList()

        coEvery { submissionDao.insertAll(any()) } just Runs
        coEvery { submissionDao.getLatestSubmissionTime(any()) } returns System.currentTimeMillis()
        coEvery { formMetadataDao.insertOrUpdate(any()) } just Runs
        coEvery { formMetadataDao.getLastSyncTimestamp(any()) } returns null
        coEvery { submissionDao.getSubmissionsSync(testAssetUid) } returns submissions
        coEvery { plotDao.findExistingSubmissionUuids(any()) } returns emptyList()
        coEvery { plotExtractor.extractPlot(any()) } returns null

        // When
        val result = repository.fetchSubmissions(testAssetUid)

        // Then - success, no crashes
        assertTrue(result.isSuccess)
        // findByInstanceNames should not be called when no drafts
        coVerify(exactly = 0) { submissionDao.findByInstanceNames(any()) }
    }

    @Test
    fun `fetchSubmissions should handle partial draft matches`() = runTest {
        // Given - API returns submissions
        val submissions = listOf(
            createSubmission("uuid-1", "instance-A"),
            createSubmission("uuid-2", "instance-B")
        )
        setupApiToReturnSubmissions(submissions)

        // Drafts include one that doesn't match any submission
        val drafts = listOf(
            createDraftPlot("draft-1", "instance-A"),
            createDraftPlot("draft-2", "instance-C")  // No matching submission
        )
        coEvery { plotDao.getAllDrafts() } returns drafts

        // Only instance-A matches
        coEvery { submissionDao.findByInstanceNames(any()) } returns listOf(
            createSubmission("uuid-1", "instance-A")
        )

        coEvery { submissionDao.insertAll(any()) } just Runs
        coEvery { submissionDao.getLatestSubmissionTime(any()) } returns System.currentTimeMillis()
        coEvery { formMetadataDao.insertOrUpdate(any()) } just Runs
        coEvery { formMetadataDao.getLastSyncTimestamp(any()) } returns null
        coEvery { submissionDao.getSubmissionsSync(testAssetUid) } returns submissions
        coEvery { plotDao.updateDraftStatus(any(), any()) } returns 1
        coEvery { plotDao.findExistingSubmissionUuids(any()) } returns emptyList()
        coEvery { plotExtractor.extractPlot(any()) } returns null

        // When
        repository.fetchSubmissions(testAssetUid)

        // Then - only matched draft updated
        coVerify(exactly = 1) { plotDao.updateDraftStatus("instance-A", "uuid-1") }
        coVerify(exactly = 0) { plotDao.updateDraftStatus("instance-C", any()) }
    }

    // ==================== extractPlotsFromSubmissions Tests ====================

    @Test
    fun `fetchSubmissions should extract plots from new submissions`() = runTest {
        // Given
        val submissions = listOf(
            createSubmission("uuid-1", "instance-A"),
            createSubmission("uuid-2", "instance-B")
        )
        setupApiToReturnSubmissions(submissions)

        coEvery { plotDao.getAllDrafts() } returns emptyList()

        // No existing plots
        coEvery { plotDao.findExistingSubmissionUuids(any()) } returns emptyList()

        // Extractor returns plots for both submissions
        val extractedPlots = listOf(
            createPlot("plot-1", "instance-A", "uuid-1"),
            createPlot("plot-2", "instance-B", "uuid-2")
        )
        coEvery { plotExtractor.extractPlot(any()) } answers {
            val submission = firstArg<SubmissionEntity>()
            when (submission._uuid) {
                "uuid-1" -> extractedPlots[0]
                "uuid-2" -> extractedPlots[1]
                else -> null
            }
        }

        coEvery { submissionDao.insertAll(any()) } just Runs
        coEvery { submissionDao.getLatestSubmissionTime(any()) } returns System.currentTimeMillis()
        coEvery { formMetadataDao.insertOrUpdate(any()) } just Runs
        coEvery { formMetadataDao.getLastSyncTimestamp(any()) } returns null
        coEvery { submissionDao.getSubmissionsSync(testAssetUid) } returns submissions
        val capturedPlots = slot<List<PlotEntity>>()
        coEvery { plotDao.insertOrUpdateAll(capture(capturedPlots)) } just Runs

        // When
        repository.fetchSubmissions(testAssetUid)

        // Then - batch insert called with extracted plots
        coVerify { plotDao.insertOrUpdateAll(any()) }
        assertEquals(2, capturedPlots.captured.size)
    }

    @Test
    fun `fetchSubmissions should skip submissions that already have plots`() = runTest {
        // Given
        val submissions = listOf(
            createSubmission("uuid-1", "instance-A"),
            createSubmission("uuid-2", "instance-B"),
            createSubmission("uuid-3", "instance-C")
        )
        setupApiToReturnSubmissions(submissions)

        coEvery { plotDao.getAllDrafts() } returns emptyList()

        // uuid-1 and uuid-2 already have plots
        coEvery { plotDao.findExistingSubmissionUuids(any()) } returns listOf("uuid-1", "uuid-2")

        // Only uuid-3 should be processed - extractor only called for that one
        val newPlot = createPlot("plot-3", "instance-C", "uuid-3")
        coEvery { plotExtractor.extractPlot(any()) } answers {
            val submission = firstArg<SubmissionEntity>()
            if (submission._uuid == "uuid-3") newPlot else null
        }

        coEvery { submissionDao.insertAll(any()) } just Runs
        coEvery { submissionDao.getLatestSubmissionTime(any()) } returns System.currentTimeMillis()
        coEvery { formMetadataDao.insertOrUpdate(any()) } just Runs
        coEvery { formMetadataDao.getLastSyncTimestamp(any()) } returns null
        coEvery { submissionDao.getSubmissionsSync(testAssetUid) } returns submissions
        val capturedPlots = slot<List<PlotEntity>>()
        coEvery { plotDao.insertOrUpdateAll(capture(capturedPlots)) } just Runs

        // When
        repository.fetchSubmissions(testAssetUid)

        // Then - only 1 plot inserted (for uuid-3)
        coVerify { plotDao.insertOrUpdateAll(any()) }
        assertEquals(1, capturedPlots.captured.size)
        assertEquals("uuid-3", capturedPlots.captured[0].submissionUuid)
    }

    @Test
    fun `fetchSubmissions should handle submissions with invalid polygon data`() = runTest {
        // Given
        val submissions = listOf(
            createSubmission("uuid-1", "instance-A"),
            createSubmission("uuid-2", "instance-B")  // Has invalid polygon
        )
        setupApiToReturnSubmissions(submissions)

        coEvery { plotDao.getAllDrafts() } returns emptyList()
        coEvery { plotDao.findExistingSubmissionUuids(any()) } returns emptyList()

        // First submission extracts successfully, second fails (returns null)
        coEvery { plotExtractor.extractPlot(any()) } answers {
            val submission = firstArg<SubmissionEntity>()
            if (submission._uuid == "uuid-1") createPlot("plot-1", "instance-A", "uuid-1") else null
        }

        coEvery { submissionDao.insertAll(any()) } just Runs
        coEvery { submissionDao.getLatestSubmissionTime(any()) } returns System.currentTimeMillis()
        coEvery { formMetadataDao.insertOrUpdate(any()) } just Runs
        coEvery { formMetadataDao.getLastSyncTimestamp(any()) } returns null
        coEvery { submissionDao.getSubmissionsSync(testAssetUid) } returns submissions
        val capturedPlots = slot<List<PlotEntity>>()
        coEvery { plotDao.insertOrUpdateAll(capture(capturedPlots)) } just Runs

        // When
        repository.fetchSubmissions(testAssetUid)

        // Then - only valid plot inserted
        coVerify { plotDao.insertOrUpdateAll(any()) }
        assertEquals(1, capturedPlots.captured.size)
        assertEquals("uuid-1", capturedPlots.captured[0].submissionUuid)
    }

    @Test
    fun `fetchSubmissions should handle all submissions having invalid polygon data`() = runTest {
        // Given
        val submissions = listOf(
            createSubmission("uuid-1", "instance-A"),
            createSubmission("uuid-2", "instance-B")
        )
        setupApiToReturnSubmissions(submissions)

        coEvery { plotDao.getAllDrafts() } returns emptyList()
        coEvery { plotDao.findExistingSubmissionUuids(any()) } returns emptyList()

        // Both fail extraction
        coEvery { plotExtractor.extractPlot(any()) } returns null

        coEvery { submissionDao.insertAll(any()) } just Runs
        coEvery { submissionDao.getLatestSubmissionTime(any()) } returns System.currentTimeMillis()
        coEvery { formMetadataDao.insertOrUpdate(any()) } just Runs
        coEvery { formMetadataDao.getLastSyncTimestamp(any()) } returns null
        coEvery { submissionDao.getSubmissionsSync(testAssetUid) } returns submissions

        // When
        val result = repository.fetchSubmissions(testAssetUid)

        // Then - success but no plots inserted
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { plotDao.insertOrUpdateAll(any()) }
    }

    @Test
    fun `fetchSubmissions should handle empty submissions list`() = runTest {
        // Given - API returns no submissions
        setupApiToReturnSubmissions(emptyList())

        // When
        val result = repository.fetchSubmissions(testAssetUid)

        // Then - success with 0 count
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())

        // No plot operations should occur
        coVerify(exactly = 0) { plotDao.getAllDrafts() }
        coVerify(exactly = 0) { plotDao.findExistingSubmissionUuids(any()) }
    }

    @Test
    fun `fetchSubmissions should use batch insert for efficiency`() = runTest {
        // Given - many submissions
        val submissions = (1..100).map { i ->
            createSubmission("uuid-$i", "instance-$i")
        }
        setupApiToReturnSubmissions(submissions)

        coEvery { plotDao.getAllDrafts() } returns emptyList()
        coEvery { plotDao.findExistingSubmissionUuids(any()) } returns emptyList()

        // All extract successfully
        coEvery { plotExtractor.extractPlot(any()) } answers {
            val submission = firstArg<SubmissionEntity>()
            val index = submission._uuid.removePrefix("uuid-").toInt()
            createPlot("plot-$index", "instance-$index", "uuid-$index")
        }

        coEvery { submissionDao.insertAll(any()) } just Runs
        coEvery { submissionDao.getLatestSubmissionTime(any()) } returns System.currentTimeMillis()
        coEvery { formMetadataDao.insertOrUpdate(any()) } just Runs
        coEvery { formMetadataDao.getLastSyncTimestamp(any()) } returns null
        coEvery { submissionDao.getSubmissionsSync(testAssetUid) } returns submissions
        coEvery { plotDao.insertOrUpdateAll(any()) } just Runs

        // When
        repository.fetchSubmissions(testAssetUid)

        // Then - single batch insert call, not 100 individual calls
        coVerify(exactly = 1) { plotDao.insertOrUpdateAll(any()) }
        coVerify(exactly = 0) { plotDao.insertOrUpdate(any()) }
    }

    // ==================== Resync Tests ====================

    @Test
    fun `resync should use batch operations for draft matching`() = runTest {
        // Given - previous sync exists
        coEvery { formMetadataDao.getLastSyncTimestamp(testAssetUid) } returns 1000L

        val submissions = listOf(createSubmission("uuid-1", "instance-A"))
        val jsonResults = submissions.map { createApiJsonObject(it._uuid, it.instanceName) }
        coEvery { apiService.getSubmissionsSince(any(), any(), any(), any()) } returns KoboDataResponse(
            count = 1,
            next = null,
            previous = null,
            results = jsonResults
        )

        val drafts = listOf(createDraftPlot("draft-1", "instance-A"))
        coEvery { plotDao.getAllDrafts() } returns drafts
        coEvery { submissionDao.findByInstanceNames(any()) } returns submissions

        coEvery { submissionDao.insertAll(any()) } just Runs
        coEvery { submissionDao.getLatestSubmissionTime(any()) } returns System.currentTimeMillis()
        coEvery { formMetadataDao.insertOrUpdate(any()) } just Runs
        coEvery { submissionDao.getSubmissionsSync(any()) } returns submissions
        coEvery { plotDao.updateDraftStatus(any(), any()) } returns 1
        coEvery { plotDao.findExistingSubmissionUuids(any()) } returns emptyList()
        coEvery { plotExtractor.extractPlot(any()) } returns null

        // When
        repository.resync(testAssetUid)

        // Then - batch query used instead of individual queries
        coVerify(exactly = 1) { submissionDao.findByInstanceNames(any()) }
        coVerify(exactly = 0) { submissionDao.findByInstanceName(any()) }
    }

    // ==================== Helper Functions ====================

    private fun createSubmission(
        uuid: String,
        instanceName: String?,
        submissionTime: Long = System.currentTimeMillis()
    ) = SubmissionEntity(
        _uuid = uuid,
        assetUid = testAssetUid,
        _id = uuid,
        submissionTime = submissionTime,
        submittedBy = "testuser",
        instanceName = instanceName,
        rawData = createRawDataJson(uuid, instanceName),
        systemData = null
    )

    private fun createRawDataJson(uuid: String, instanceName: String?): String {
        val instanceNameField = if (instanceName != null) {
            """"meta/instanceName":"$instanceName","""
        } else {
            ""
        }
        return """{
            "_uuid":"$uuid",
            "_id":12345,
            "_submission_time":"2024-03-15T07:45:53",
            "_submitted_by":"testuser",
            $instanceNameField
            "region":"test-region"
        }""".trimIndent()
    }

    private fun createDraftPlot(
        uuid: String,
        instanceName: String
    ) = PlotEntity(
        uuid = uuid,
        plotName = "Test Plot",
        instanceName = instanceName,
        polygonWkt = "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))",
        minLat = 0.0,
        maxLat = 10.0,
        minLon = 0.0,
        maxLon = 10.0,
        isDraft = true,
        formId = testAssetUid,
        region = "test-region",
        subRegion = "test-sub",
        submissionUuid = null
    )

    private fun createPlot(
        uuid: String,
        instanceName: String,
        submissionUuid: String
    ) = PlotEntity(
        uuid = uuid,
        plotName = "Test Plot",
        instanceName = instanceName,
        polygonWkt = "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))",
        minLat = 0.0,
        maxLat = 10.0,
        minLon = 0.0,
        maxLon = 10.0,
        isDraft = false,
        formId = testAssetUid,
        region = "test-region",
        subRegion = "test-sub",
        submissionUuid = submissionUuid
    )

    /**
     * Creates a proper JSON object with all required Kobo fields for API response simulation.
     */
    private fun createApiJsonObject(uuid: String, instanceName: String?): kotlinx.serialization.json.JsonObject {
        val fields = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
            "_uuid" to kotlinx.serialization.json.JsonPrimitive(uuid),
            "_id" to kotlinx.serialization.json.JsonPrimitive(12345),
            "_submission_time" to kotlinx.serialization.json.JsonPrimitive("2024-03-15T07:45:53"),
            "_submitted_by" to kotlinx.serialization.json.JsonPrimitive("testuser"),
            "region" to kotlinx.serialization.json.JsonPrimitive("test-region")
        )
        if (instanceName != null) {
            fields["meta/instanceName"] = kotlinx.serialization.json.JsonPrimitive(instanceName)
        }
        return kotlinx.serialization.json.JsonObject(fields)
    }

    private fun setupApiToReturnSubmissions(submissions: List<SubmissionEntity>) {
        val jsonResults = submissions.map { submission ->
            createApiJsonObject(submission._uuid, submission.instanceName)
        }
        coEvery { apiService.getSubmissions(any(), any(), any()) } returns KoboDataResponse(
            count = submissions.size,
            next = null,
            previous = null,
            results = jsonResults
        )
    }

}