package org.akvo.afribamodkvalidator.data.repository

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.toList
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
 * - fetchSubmissions: initial download with progress
 * - resync: delta sync + validation reconciliation
 * - matchDraftsToSubmissions: draft-to-submission linking logic
 * - extractPlotsFromSubmissions: plot extraction from synced submissions
 *
 * Edge cases covered:
 * - Multiple drafts with same instanceName
 * - Submissions without instanceName
 * - Partial failures during bulk processing
 * - Submissions with invalid/incomplete polygon data
 * - Re-approved submissions restored via reconciliation
 * - Newly rejected old submissions removed via reconciliation
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
        repository.fetchSubmissions(testAssetUid).toList()

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
        repository.fetchSubmissions(testAssetUid).toList()

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
        repository.fetchSubmissions(testAssetUid).toList()

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
        val events = repository.fetchSubmissions(testAssetUid).toList()

        // Then - success, no crashes
        val complete = events.last() as SyncProgress.Complete
        assertEquals(1, complete.inserted)
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
        repository.fetchSubmissions(testAssetUid).toList()

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
        repository.fetchSubmissions(testAssetUid).toList()

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
        repository.fetchSubmissions(testAssetUid).toList()

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
        repository.fetchSubmissions(testAssetUid).toList()

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
        val events = repository.fetchSubmissions(testAssetUid).toList()

        // Then - success but no plots inserted
        val complete = events.last() as SyncProgress.Complete
        assertEquals(2, complete.inserted)
        coVerify(exactly = 0) { plotDao.insertOrUpdateAll(any()) }
    }

    @Test
    fun `fetchSubmissions should handle empty submissions list`() = runTest {
        // Given - API returns no submissions
        setupApiToReturnSubmissions(emptyList())

        // When
        val events = repository.fetchSubmissions(testAssetUid).toList()

        // Then - Complete with 0 count
        val complete = events.last() as SyncProgress.Complete
        assertEquals(0, complete.inserted)

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
        repository.fetchSubmissions(testAssetUid).toList()

        // Then - single batch insert call, not 100 individual calls
        coVerify(exactly = 1) { plotDao.insertOrUpdateAll(any()) }
        coVerify(exactly = 0) { plotDao.insertOrUpdate(any()) }
    }

    // ==================== Rejection Filter Tests ====================

    @Test
    fun `fetchSubmissions should skip rejected submissions`() = runTest {
        // Given - API returns mix of approved and rejected submissions
        val jsonResults = listOf(
            createApiJsonObject("uuid-1", "instance-A"),
            createApiJsonObject("uuid-2", "instance-B", validationStatus = "validation_status_not_approved"),
            createApiJsonObject("uuid-3", "instance-C")
        )
        coEvery { apiService.getSubmissions(any(), any(), any()) } returns KoboDataResponse(
            count = 3, next = null, previous = null, results = jsonResults
        )

        coEvery { plotDao.getAllDrafts() } returns emptyList()
        coEvery { submissionDao.insertAll(any()) } just Runs
        coEvery { submissionDao.getLatestSubmissionTime(any()) } returns System.currentTimeMillis()
        coEvery { formMetadataDao.insertOrUpdate(any()) } just Runs
        coEvery { formMetadataDao.getLastSyncTimestamp(any()) } returns null
        coEvery { submissionDao.getSubmissionsSync(testAssetUid) } returns emptyList()
        coEvery { plotDao.findExistingSubmissionUuids(any()) } returns emptyList()

        // When
        val events = repository.fetchSubmissions(testAssetUid).toList()

        // Then - only 2 approved submissions inserted
        val complete = events.last() as SyncProgress.Complete
        assertEquals(2, complete.inserted)
        assertEquals(1, complete.rejected)
        val captured = slot<List<SubmissionEntity>>()
        coVerify { submissionDao.insertAll(capture(captured)) }
        assertEquals(2, captured.captured.size)
        assertTrue(captured.captured.none { it._uuid == "uuid-2" })
    }

    @Test
    fun `fetchSubmissions should handle all submissions being rejected`() = runTest {
        // Given - all submissions are rejected
        val jsonResults = listOf(
            createApiJsonObject("uuid-1", "instance-A", validationStatus = "validation_status_not_approved"),
            createApiJsonObject("uuid-2", "instance-B", validationStatus = "validation_status_not_approved")
        )
        coEvery { apiService.getSubmissions(any(), any(), any()) } returns KoboDataResponse(
            count = 2, next = null, previous = null, results = jsonResults
        )
        coEvery { formMetadataDao.getLastSyncTimestamp(any()) } returns null

        // When
        val events = repository.fetchSubmissions(testAssetUid).toList()

        // Then - zero insertions
        val complete = events.last() as SyncProgress.Complete
        assertEquals(0, complete.inserted)
        assertEquals(2, complete.rejected)
        coVerify(exactly = 0) { submissionDao.insertAll(any()) }
    }

    @Test
    fun `fetchSubmissions should treat empty validation_status as approved`() = runTest {
        // Given - submission with empty validation_status (common for new submissions)
        val jsonResults = listOf(
            createApiJsonObject("uuid-1", "instance-A", validationStatus = null),
            createApiJsonObject("uuid-2", "instance-B")  // no validation status at all
        )
        coEvery { apiService.getSubmissions(any(), any(), any()) } returns KoboDataResponse(
            count = 2, next = null, previous = null, results = jsonResults
        )

        coEvery { plotDao.getAllDrafts() } returns emptyList()
        coEvery { submissionDao.insertAll(any()) } just Runs
        coEvery { submissionDao.getLatestSubmissionTime(any()) } returns System.currentTimeMillis()
        coEvery { formMetadataDao.insertOrUpdate(any()) } just Runs
        coEvery { formMetadataDao.getLastSyncTimestamp(any()) } returns null
        coEvery { submissionDao.getSubmissionsSync(testAssetUid) } returns emptyList()
        coEvery { plotDao.findExistingSubmissionUuids(any()) } returns emptyList()

        // When
        val events = repository.fetchSubmissions(testAssetUid).toList()

        // Then - both treated as approved
        val complete = events.last() as SyncProgress.Complete
        assertEquals(2, complete.inserted)
    }

    // ==================== Resync + Reconciliation Tests ====================

    @Test
    fun `resync should delete newly-rejected old submissions via reconciliation`() = runTest {
        // Given - previous sync exists
        coEvery { formMetadataDao.getLastSyncTimestamp(testAssetUid) } returns 1000000L

        // Delta sync returns nothing new
        coEvery {
            apiService.getSubmissionsSince(any(), match { it.contains("_submission_time") }, any(), any())
        } returns KoboDataResponse(count = 0, next = null, previous = null, results = emptyList())

        // Reconciliation query finds a newly-rejected submission
        val rejectedJson = listOf(
            createApiJsonObject("uuid-1", "instance-A", validationStatus = "validation_status_not_approved")
        )
        coEvery {
            apiService.getSubmissionsWithFields(any(), any(), any(), any(), any())
        } returns KoboDataResponse(count = 1, next = null, previous = null, results = rejectedJson)

        coEvery { plotDao.deleteBySubmissionUuids(any()) } returns 1
        coEvery { submissionDao.deleteByUuids(any()) } returns 1
        coEvery { formMetadataDao.insertOrUpdate(any()) } just Runs

        // When
        val events = repository.resync(testAssetUid).toList()

        // Then - submission deleted via reconciliation
        val complete = events.last() as SyncProgress.Complete
        assertEquals(0, complete.inserted)
        assertEquals(1, complete.rejected)
        coVerify { submissionDao.deleteByUuids(listOf("uuid-1")) }
        coVerify { plotDao.deleteBySubmissionUuids(listOf("uuid-1")) }
        // Sync timestamp advanced so reconciliation window isn't reprocessed
        coVerify { formMetadataDao.insertOrUpdate(any()) }
    }

    @Test
    fun `resync should restore re-approved submissions`() = runTest {
        // Given - previous sync exists
        coEvery { formMetadataDao.getLastSyncTimestamp(testAssetUid) } returns 1000000L

        // Delta sync returns nothing new
        coEvery {
            apiService.getSubmissionsSince(any(), match { it.contains("_submission_time") }, any(), any())
        } returns KoboDataResponse(count = 0, next = null, previous = null, results = emptyList())

        // Reconciliation finds a re-approved submission (status changed but NOT rejected)
        val reApprovedJson = listOf(
            createApiJsonObject("uuid-1", "instance-A", validationStatus = "validation_status_approved")
        )
        coEvery {
            apiService.getSubmissionsWithFields(any(), any(), any(), any(), any())
        } returns KoboDataResponse(count = 1, next = null, previous = null, results = reApprovedJson)

        // Re-fetch full data for re-approved submission
        val fullDataJson = listOf(
            createApiJsonObject("uuid-1", "instance-A", validationStatus = "validation_status_approved")
        )
        coEvery {
            apiService.getSubmissionsSince(any(), match { it.contains("_uuid") }, any(), any())
        } returns KoboDataResponse(count = 1, next = null, previous = null, results = fullDataJson)

        coEvery { submissionDao.insertAll(any()) } just Runs
        coEvery { submissionDao.getLatestSubmissionTime(any()) } returns System.currentTimeMillis()
        coEvery { formMetadataDao.insertOrUpdate(any()) } just Runs
        coEvery { submissionDao.getSubmissionsSync(any()) } returns emptyList()
        coEvery { plotDao.getAllDrafts() } returns emptyList()
        coEvery { plotDao.findExistingSubmissionUuids(any()) } returns emptyList()

        // When
        val events = repository.resync(testAssetUid).toList()

        // Then - submission re-fetched and inserted
        val complete = events.last() as SyncProgress.Complete
        assertEquals(0, complete.inserted) // inserted counts delta sync only
        assertEquals(1, complete.restored) // restored counts reconciliation restores
        assertEquals(0, complete.rejected)
        // Verify the re-fetch query was made
        coVerify {
            apiService.getSubmissionsSince(any(), match { it.contains("_uuid") }, any(), any())
        }
        // Verify submission was inserted
        coVerify { submissionDao.insertAll(any()) }
        // Verify post-processing ran (draft matching + plot extraction)
        coVerify { plotDao.getAllDrafts() }
    }

    @Test
    fun `resync should handle multiple rejected submissions from reconciliation`() = runTest {
        // Given - previous sync exists
        coEvery { formMetadataDao.getLastSyncTimestamp(testAssetUid) } returns 1000000L

        // Delta sync returns nothing new
        coEvery {
            apiService.getSubmissionsSince(any(), match { it.contains("_submission_time") }, any(), any())
        } returns KoboDataResponse(count = 0, next = null, previous = null, results = emptyList())

        // Reconciliation finds multiple rejected submissions
        val rejectedJson = listOf(
            createApiJsonObject("uuid-1", "instance-A", validationStatus = "validation_status_not_approved"),
            createApiJsonObject("uuid-2", "instance-B", validationStatus = "validation_status_not_approved")
        )
        coEvery {
            apiService.getSubmissionsWithFields(any(), any(), any(), any(), any())
        } returns KoboDataResponse(count = 2, next = null, previous = null, results = rejectedJson)

        coEvery { plotDao.deleteBySubmissionUuids(any()) } returns 2
        coEvery { submissionDao.deleteByUuids(any()) } returns 2
        coEvery { formMetadataDao.insertOrUpdate(any()) } just Runs

        // When
        val events = repository.resync(testAssetUid).toList()

        // Then - cleanup deletes both
        val complete = events.last() as SyncProgress.Complete
        assertEquals(0, complete.inserted)
        assertEquals(2, complete.rejected)
        coVerify { submissionDao.deleteByUuids(listOf("uuid-1", "uuid-2")) }
        coVerify { plotDao.deleteBySubmissionUuids(listOf("uuid-1", "uuid-2")) }
    }

    @Test
    fun `resync should not call removeRejected when no status changes found`() = runTest {
        // Given - previous sync exists
        coEvery { formMetadataDao.getLastSyncTimestamp(testAssetUid) } returns 1000000L

        // Delta sync returns approved submissions
        val submissions = listOf(createSubmission("uuid-1", "instance-A"))
        val jsonResults = submissions.map { createApiJsonObject(it._uuid, it.instanceName) }
        coEvery {
            apiService.getSubmissionsSince(any(), match { it.contains("_submission_time") }, any(), any())
        } returns KoboDataResponse(count = 1, next = null, previous = null, results = jsonResults)

        // Reconciliation finds nothing
        coEvery {
            apiService.getSubmissionsWithFields(any(), any(), any(), any(), any())
        } returns KoboDataResponse(count = 0, next = null, previous = null, results = emptyList())

        coEvery { plotDao.getAllDrafts() } returns emptyList()
        coEvery { submissionDao.insertAll(any()) } just Runs
        coEvery { submissionDao.getLatestSubmissionTime(any()) } returns System.currentTimeMillis()
        coEvery { formMetadataDao.insertOrUpdate(any()) } just Runs
        coEvery { submissionDao.getSubmissionsSync(any()) } returns submissions
        coEvery { plotDao.findExistingSubmissionUuids(any()) } returns emptyList()
        coEvery { plotExtractor.extractPlot(any()) } returns null

        // When
        repository.resync(testAssetUid).toList()

        // Then - no delete calls
        coVerify(exactly = 0) { submissionDao.deleteByUuids(any()) }
        coVerify(exactly = 0) { plotDao.deleteBySubmissionUuids(any()) }
    }

    @Test
    fun `resync should use batch operations for draft matching`() = runTest {
        // Given - previous sync exists
        coEvery { formMetadataDao.getLastSyncTimestamp(testAssetUid) } returns 1000000L

        val submissions = listOf(createSubmission("uuid-1", "instance-A"))
        val jsonResults = submissions.map { createApiJsonObject(it._uuid, it.instanceName) }
        coEvery {
            apiService.getSubmissionsSince(any(), match { it.contains("_submission_time") }, any(), any())
        } returns KoboDataResponse(count = 1, next = null, previous = null, results = jsonResults)

        // No status changes from reconciliation
        coEvery {
            apiService.getSubmissionsWithFields(any(), any(), any(), any(), any())
        } returns KoboDataResponse(count = 0, next = null, previous = null, results = emptyList())

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
        repository.resync(testAssetUid).toList()

        // Then - batch query used instead of individual queries
        coVerify(exactly = 1) { submissionDao.findByInstanceNames(any()) }
        coVerify(exactly = 0) { submissionDao.findByInstanceName(any()) }
    }

    // ==================== Progress Events Tests ====================

    @Test
    fun `fetchSubmissions should emit progress events per page`() = runTest {
        // Given - API returns 2 pages of submissions
        val page1Results = (1..3).map { createApiJsonObject("uuid-$it", "instance-$it") }
        val page2Results = (4..5).map { createApiJsonObject("uuid-$it", "instance-$it") }

        coEvery { apiService.getSubmissions(any(), any(), eq(0)) } returns KoboDataResponse(
            count = 5, next = "http://next", previous = null, results = page1Results
        )
        coEvery { apiService.getSubmissions(any(), any(), eq(300)) } returns KoboDataResponse(
            count = 5, next = null, previous = null, results = page2Results
        )

        coEvery { plotDao.getAllDrafts() } returns emptyList()
        coEvery { submissionDao.insertAll(any()) } just Runs
        coEvery { submissionDao.getLatestSubmissionTime(any()) } returns System.currentTimeMillis()
        coEvery { formMetadataDao.insertOrUpdate(any()) } just Runs
        coEvery { formMetadataDao.getLastSyncTimestamp(any()) } returns null
        coEvery { submissionDao.getSubmissionsSync(testAssetUid) } returns emptyList()
        coEvery { plotDao.findExistingSubmissionUuids(any()) } returns emptyList()

        // When
        val events = repository.fetchSubmissions(testAssetUid).toList()

        // Then - should have Downloading events + Complete
        assertTrue(events.size >= 2)
        assertTrue(events.dropLast(1).all { it is SyncProgress.Downloading })
        assertTrue(events.last() is SyncProgress.Complete)
        val complete = events.last() as SyncProgress.Complete
        assertEquals(5, complete.inserted)
    }

    @Test
    fun `resync with no previous sync should fall back to full fetch`() = runTest {
        // Given - no previous sync
        coEvery { formMetadataDao.getLastSyncTimestamp(testAssetUid) } returns null

        val submissions = listOf(createSubmission("uuid-1", "instance-A"))
        setupApiToReturnSubmissions(submissions)

        coEvery { plotDao.getAllDrafts() } returns emptyList()
        coEvery { submissionDao.insertAll(any()) } just Runs
        coEvery { submissionDao.getLatestSubmissionTime(any()) } returns System.currentTimeMillis()
        coEvery { formMetadataDao.insertOrUpdate(any()) } just Runs
        coEvery { submissionDao.getSubmissionsSync(testAssetUid) } returns submissions
        coEvery { plotDao.findExistingSubmissionUuids(any()) } returns emptyList()
        coEvery { plotExtractor.extractPlot(any()) } returns null

        // When
        val events = repository.resync(testAssetUid).toList()

        // Then - uses full fetch (getSubmissions, not getSubmissionsSince)
        val complete = events.last() as SyncProgress.Complete
        assertEquals(1, complete.inserted)
        coVerify { apiService.getSubmissions(any(), any(), any()) }
        coVerify(exactly = 0) { apiService.getSubmissionsSince(any(), any(), any(), any()) }
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
    private fun createApiJsonObject(
        uuid: String,
        instanceName: String?,
        validationStatus: String? = null
    ): kotlinx.serialization.json.JsonObject {
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
        if (validationStatus != null) {
            fields["_validation_status"] = kotlinx.serialization.json.JsonObject(
                mapOf("uid" to kotlinx.serialization.json.JsonPrimitive(validationStatus))
            )
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
