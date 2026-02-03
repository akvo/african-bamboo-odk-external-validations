package org.akvo.afribamodkvalidator.data.dao

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.akvo.afribamodkvalidator.data.database.DatabaseTest
import org.akvo.afribamodkvalidator.data.entity.PlotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for PlotDao.
 *
 * Tests cover:
 * - Basic CRUD operations
 * - Bounding box intersection logic for overlap detection
 * - Region filtering
 * - Draft status updates
 */
class PlotDaoTest : DatabaseTest() {

    private val plotDao: PlotDao by lazy { database.plotDao() }

    // ==================== Helper Functions ====================

    private fun createPlot(
        uuid: String = "test-uuid",
        plotName: String = "Test Plot",
        instanceName: String = "test-instance",
        region: String = "test-region",
        subRegion: String = "test-sub-region",
        formId: String = "test-form",
        minLat: Double = 0.0,
        maxLat: Double = 10.0,
        minLon: Double = 0.0,
        maxLon: Double = 10.0,
        isDraft: Boolean = true,
        submissionUuid: String? = null
    ) = PlotEntity(
        uuid = uuid,
        plotName = plotName,
        instanceName = instanceName,
        polygonWkt = "POLYGON (($minLon $minLat, $maxLon $minLat, $maxLon $maxLat, $minLon $maxLat, $minLon $minLat))",
        minLat = minLat,
        maxLat = maxLat,
        minLon = minLon,
        maxLon = maxLon,
        isDraft = isDraft,
        formId = formId,
        region = region,
        subRegion = subRegion,
        submissionUuid = submissionUuid
    )

    // ==================== Basic CRUD Tests ====================

    @Test
    fun `insertOrUpdate should insert new plot`() = runTest {
        val plot = createPlot(uuid = "plot-1")

        plotDao.insertOrUpdate(plot)

        val retrieved = plotDao.getByUuid("plot-1")
        assertNotNull(retrieved)
        assertEquals("plot-1", retrieved?.uuid)
        assertEquals("Test Plot", retrieved?.plotName)
    }

    @Test
    fun `insertOrUpdate should replace existing plot with same UUID`() = runTest {
        val original = createPlot(uuid = "plot-1", plotName = "Original Name")
        plotDao.insertOrUpdate(original)

        val updated = original.copy(plotName = "Updated Name")
        plotDao.insertOrUpdate(updated)

        val retrieved = plotDao.getByUuid("plot-1")
        assertEquals("Updated Name", retrieved?.plotName)

        val all = plotDao.getAll()
        assertEquals(1, all.size)
    }

    @Test
    fun `insertOrUpdateAll should insert multiple plots`() = runTest {
        val plots = listOf(
            createPlot(uuid = "plot-1", plotName = "Plot One"),
            createPlot(uuid = "plot-2", plotName = "Plot Two"),
            createPlot(uuid = "plot-3", plotName = "Plot Three")
        )

        plotDao.insertOrUpdateAll(plots)

        val all = plotDao.getAll()
        assertEquals(3, all.size)
    }

    @Test
    fun `getByUuid should return null for non-existent UUID`() = runTest {
        val result = plotDao.getByUuid("non-existent")
        assertNull(result)
    }

    @Test
    fun `deleteByUuid should remove plot`() = runTest {
        val plot = createPlot(uuid = "plot-1")
        plotDao.insertOrUpdate(plot)

        val deleted = plotDao.deleteByUuid("plot-1")

        assertEquals(1, deleted)
        assertNull(plotDao.getByUuid("plot-1"))
    }

    @Test
    fun `deleteAll should remove all plots`() = runTest {
        plotDao.insertOrUpdateAll(listOf(
            createPlot(uuid = "plot-1"),
            createPlot(uuid = "plot-2"),
            createPlot(uuid = "plot-3")
        ))

        val deleted = plotDao.deleteAll()

        assertEquals(3, deleted)
        assertEquals(0, plotDao.getAll().size)
    }

    // ==================== Query Tests ====================

    @Test
    fun `getAll should return plots ordered by createdAt DESC`() = runTest {
        // Insert with different createdAt times
        val plots = listOf(
            createPlot(uuid = "oldest").copy(createdAt = 1000L),
            createPlot(uuid = "newest").copy(createdAt = 3000L),
            createPlot(uuid = "middle").copy(createdAt = 2000L)
        )
        plotDao.insertOrUpdateAll(plots)

        val all = plotDao.getAll()

        assertEquals(3, all.size)
        assertEquals("newest", all[0].uuid)
        assertEquals("middle", all[1].uuid)
        assertEquals("oldest", all[2].uuid)
    }

    @Test
    fun `getAllFlow should emit updates`() = runTest {
        plotDao.getAllFlow().test {
            // Initial empty state
            var plots = awaitItem()
            assertTrue(plots.isEmpty())

            // Insert a plot
            plotDao.insertOrUpdate(createPlot(uuid = "plot-1"))

            // Should emit updated list
            plots = awaitItem()
            assertEquals(1, plots.size)

            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `getByFormId should filter by form`() = runTest {
        plotDao.insertOrUpdateAll(listOf(
            createPlot(uuid = "plot-1", formId = "form-A"),
            createPlot(uuid = "plot-2", formId = "form-A"),
            createPlot(uuid = "plot-3", formId = "form-B")
        ))

        val formAPlots = plotDao.getByFormId("form-A")
        val formBPlots = plotDao.getByFormId("form-B")

        assertEquals(2, formAPlots.size)
        assertEquals(1, formBPlots.size)
    }

    @Test
    fun `getPlotsByUuids should return matching plots`() = runTest {
        plotDao.insertOrUpdateAll(listOf(
            createPlot(uuid = "plot-1"),
            createPlot(uuid = "plot-2"),
            createPlot(uuid = "plot-3")
        ))

        val result = plotDao.getPlotsByUuids(listOf("plot-1", "plot-3"))

        assertEquals(2, result.size)
        assertTrue(result.any { it.uuid == "plot-1" })
        assertTrue(result.any { it.uuid == "plot-3" })
    }

    @Test
    fun `findByInstanceName should return matching plot`() = runTest {
        plotDao.insertOrUpdate(createPlot(uuid = "plot-1", instanceName = "instance-123"))

        val result = plotDao.findByInstanceName("instance-123")

        assertNotNull(result)
        assertEquals("plot-1", result?.uuid)
    }

    @Test
    fun `findByInstanceName should return null for no match`() = runTest {
        plotDao.insertOrUpdate(createPlot(uuid = "plot-1", instanceName = "instance-123"))

        val result = plotDao.findByInstanceName("different-instance")

        assertNull(result)
    }

    // ==================== Bounding Box Intersection Tests ====================

    @Test
    fun `findOverlapCandidates should return overlapping bbox`() = runTest {
        // Existing plot: 0-10 x 0-10
        val existing = createPlot(
            uuid = "existing",
            region = "region-A",
            minLat = 0.0, maxLat = 10.0,
            minLon = 0.0, maxLon = 10.0
        )
        plotDao.insertOrUpdate(existing)

        // Query bbox: 5-15 x 5-15 (overlaps with existing)
        val candidates = plotDao.findOverlapCandidates(
            minLat = 5.0, maxLat = 15.0,
            minLon = 5.0, maxLon = 15.0,
            excludeUuid = "new-plot"
        )

        assertEquals(1, candidates.size)
        assertEquals("existing", candidates[0].uuid)
    }

    @Test
    fun `findOverlapCandidates should not return non-overlapping bbox`() = runTest {
        // Existing plot: 0-10 x 0-10
        val existing = createPlot(
            uuid = "existing",
            region = "region-A",
            minLat = 0.0, maxLat = 10.0,
            minLon = 0.0, maxLon = 10.0
        )
        plotDao.insertOrUpdate(existing)

        // Query bbox: 20-30 x 20-30 (no overlap)
        val candidates = plotDao.findOverlapCandidates(
            minLat = 20.0, maxLat = 30.0,
            minLon = 20.0, maxLon = 30.0,
            excludeUuid = "new-plot"
        )

        assertEquals(0, candidates.size)
    }

    @Test
    fun `findOverlapCandidates should find plots across different regions`() = runTest {
        // Two plots with overlapping bboxes but different regions
        // Region is just a label - overlap detection should find both
        plotDao.insertOrUpdateAll(listOf(
            createPlot(uuid = "plot-A", region = "region-A", minLat = 0.0, maxLat = 10.0, minLon = 0.0, maxLon = 10.0),
            createPlot(uuid = "plot-B", region = "region-B", minLat = 0.0, maxLat = 10.0, minLon = 0.0, maxLon = 10.0)
        ))

        // Query should find both regardless of region
        val candidates = plotDao.findOverlapCandidates(
            minLat = 0.0, maxLat = 10.0,
            minLon = 0.0, maxLon = 10.0,
            excludeUuid = "new-plot"
        )

        assertEquals(2, candidates.size)
        assertTrue(candidates.any { it.uuid == "plot-A" })
        assertTrue(candidates.any { it.uuid == "plot-B" })
    }

    @Test
    fun `findOverlapCandidates should exclude specified UUID`() = runTest {
        plotDao.insertOrUpdate(createPlot(
            uuid = "self",
            region = "region-A",
            minLat = 0.0, maxLat = 10.0,
            minLon = 0.0, maxLon = 10.0
        ))

        // Query with same bbox but exclude self
        val candidates = plotDao.findOverlapCandidates(
            minLat = 0.0, maxLat = 10.0,
            minLon = 0.0, maxLon = 10.0,
            excludeUuid = "self"
        )

        assertEquals(0, candidates.size)
    }

    @Test
    fun `findOverlapCandidates should detect touching edge as overlap`() = runTest {
        // Existing plot: 0-10 x 0-10
        val existing = createPlot(
            uuid = "existing",
            region = "region-A",
            minLat = 0.0, maxLat = 10.0,
            minLon = 0.0, maxLon = 10.0
        )
        plotDao.insertOrUpdate(existing)

        // Query bbox: 10-20 x 0-10 (shares edge at x=10)
        val candidates = plotDao.findOverlapCandidates(
            minLat = 0.0, maxLat = 10.0,
            minLon = 10.0, maxLon = 20.0,
            excludeUuid = "new-plot"
        )

        // Touching edge counts as bbox overlap (JTS will determine actual polygon overlap)
        assertEquals(1, candidates.size)
    }

    @Test
    fun `findOverlapCandidates should detect partial overlap on one axis only as non-overlap`() = runTest {
        // Existing plot: 0-10 x 0-10
        val existing = createPlot(
            uuid = "existing",
            region = "region-A",
            minLat = 0.0, maxLat = 10.0,
            minLon = 0.0, maxLon = 10.0
        )
        plotDao.insertOrUpdate(existing)

        // Query bbox: 5-15 x 20-30 (overlaps on X axis but not Y axis)
        val candidates = plotDao.findOverlapCandidates(
            minLat = 20.0, maxLat = 30.0,
            minLon = 5.0, maxLon = 15.0,
            excludeUuid = "new-plot"
        )

        // Must overlap on BOTH axes to be a candidate
        assertEquals(0, candidates.size)
    }

    @Test
    fun `findOverlapCandidates should return multiple overlapping plots`() = runTest {
        // Create 3 plots that all overlap with query bbox (different regions)
        plotDao.insertOrUpdateAll(listOf(
            createPlot(uuid = "plot-1", region = "region-A", minLat = 0.0, maxLat = 10.0, minLon = 0.0, maxLon = 10.0),
            createPlot(uuid = "plot-2", region = "region-B", minLat = 5.0, maxLat = 15.0, minLon = 5.0, maxLon = 15.0),
            createPlot(uuid = "plot-3", region = "region-C", minLat = 8.0, maxLat = 12.0, minLon = 8.0, maxLon = 12.0)
        ))

        // Query bbox: 7-11 x 7-11 (overlaps with all 3)
        val candidates = plotDao.findOverlapCandidates(
            minLat = 7.0, maxLat = 11.0,
            minLon = 7.0, maxLon = 11.0,
            excludeUuid = "new-plot"
        )

        assertEquals(3, candidates.size)
    }

    @Test
    fun `findOverlapCandidates catches same plot registered in different region`() = runTest {
        // Simulate fraud: same plot registered in different region
        val existingPlot = createPlot(
            uuid = "original-plot",
            plotName = "Farmer A Plot",
            region = "woreda-1",
            minLat = 9.0, maxLat = 9.1,
            minLon = 38.0, maxLon = 38.1
        )
        plotDao.insertOrUpdate(existingPlot)

        // Query finds the duplicate regardless of region label
        val candidates = plotDao.findOverlapCandidates(
            minLat = 9.0, maxLat = 9.1,
            minLon = 38.0, maxLon = 38.1,
            excludeUuid = "new-registration"
        )

        assertEquals(1, candidates.size)
        assertEquals("original-plot", candidates[0].uuid)
    }

    // ==================== Draft Status Update Tests ====================

    @Test
    fun `updateDraftStatus should update draft plot`() = runTest {
        val draft = createPlot(uuid = "plot-1", instanceName = "instance-123", isDraft = true)
        plotDao.insertOrUpdate(draft)

        val updated = plotDao.updateDraftStatus("instance-123", "submission-uuid")

        assertEquals(1, updated)

        val retrieved = plotDao.getByUuid("plot-1")
        assertFalse(retrieved!!.isDraft)
        assertEquals("submission-uuid", retrieved.submissionUuid)
    }

    @Test
    fun `updateDraftStatus should not update already synced plot`() = runTest {
        // Insert a non-draft (already synced) plot
        val synced = createPlot(
            uuid = "plot-1",
            instanceName = "instance-123",
            isDraft = false,
            submissionUuid = "original-submission"
        )
        plotDao.insertOrUpdate(synced)

        // Try to update - should not change because isDraft = false
        val updated = plotDao.updateDraftStatus("instance-123", "new-submission-uuid")

        assertEquals(0, updated)

        val retrieved = plotDao.getByUuid("plot-1")
        assertFalse(retrieved!!.isDraft)
        assertEquals("original-submission", retrieved.submissionUuid)
    }

    @Test
    fun `updateDraftStatus should return 0 for non-existent instanceName`() = runTest {
        val updated = plotDao.updateDraftStatus("non-existent", "submission-uuid")
        assertEquals(0, updated)
    }
}
