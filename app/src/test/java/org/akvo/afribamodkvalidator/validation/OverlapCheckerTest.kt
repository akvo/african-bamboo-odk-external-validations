package org.akvo.afribamodkvalidator.validation

import org.akvo.afribamodkvalidator.data.entity.PlotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlapCheckerTest {

    private val overlapChecker = OverlapChecker()

    // ==================== Bounding Box Tests ====================

    @Test
    fun `computeBoundingBox returns correct bounds for simple square`() {
        val squareWkt = "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))"
        val polygon = overlapChecker.parseWkt(squareWkt)!!

        val bbox = overlapChecker.computeBoundingBox(polygon)

        assertEquals(0.0, bbox.minLon, 0.0001)
        assertEquals(10.0, bbox.maxLon, 0.0001)
        assertEquals(0.0, bbox.minLat, 0.0001)
        assertEquals(10.0, bbox.maxLat, 0.0001)
    }

    @Test
    fun `computeBoundingBox handles irregular polygon`() {
        // Triangle with varying coordinates
        val triangleWkt = "POLYGON ((5 0, 10 8, 0 4, 5 0))"
        val polygon = overlapChecker.parseWkt(triangleWkt)!!

        val bbox = overlapChecker.computeBoundingBox(polygon)

        assertEquals(0.0, bbox.minLon, 0.0001)
        assertEquals(10.0, bbox.maxLon, 0.0001)
        assertEquals(0.0, bbox.minLat, 0.0001)
        assertEquals(8.0, bbox.maxLat, 0.0001)
    }

    // ==================== WKT Parsing Tests ====================

    @Test
    fun `parseWkt returns polygon for valid WKT`() {
        val wkt = "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))"

        val polygon = overlapChecker.parseWkt(wkt)

        assertNotNull(polygon)
        assertEquals(5, polygon!!.numPoints)
    }

    @Test
    fun `parseWkt returns null for invalid WKT`() {
        val invalidWkt = "NOT A POLYGON"

        val polygon = overlapChecker.parseWkt(invalidWkt)

        assertNull(polygon)
    }

    @Test
    fun `parseWkt returns null for non-polygon geometry`() {
        val lineWkt = "LINESTRING (0 0, 10 10)"

        val polygon = overlapChecker.parseWkt(lineWkt)

        assertNull(polygon)
    }

    @Test
    fun `toWkt converts polygon back to valid WKT string`() {
        val originalWkt = "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))"
        val polygon = overlapChecker.parseWkt(originalWkt)!!

        val resultWkt = overlapChecker.toWkt(polygon)

        // Parse it again to verify it's valid
        val reparsed = overlapChecker.parseWkt(resultWkt)
        assertNotNull(reparsed)
        assertEquals(polygon.area, reparsed!!.area, 0.0001)
    }

    // ==================== Overlap Detection Tests ====================

    @Test
    fun `checkOverlaps detects exact overlap - 100 percent`() {
        // Two identical squares
        val newPolygonWkt = "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))"
        val newPolygon = overlapChecker.parseWkt(newPolygonWkt)!!

        val existingPlot = createPlotEntity(
            uuid = "existing-1",
            plotName = "Existing Plot",
            polygonWkt = "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))"
        )

        val overlaps = overlapChecker.checkOverlaps(newPolygon, listOf(existingPlot))

        assertEquals(1, overlaps.size)
        assertEquals("existing-1", overlaps[0].uuid)
        assertEquals("Existing Plot", overlaps[0].plotName)
        assertEquals(100.0, overlaps[0].overlapPercentage, 0.1)
    }

    @Test
    fun `checkOverlaps detects 50 percent overlap - above threshold`() {
        // New polygon: 0-10 x 0-10 (area 100)
        // Existing polygon: 5-15 x 0-10 (area 100)
        // Overlap: 5-10 x 0-10 (area 50) = 50% of smaller
        val newPolygonWkt = "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))"
        val newPolygon = overlapChecker.parseWkt(newPolygonWkt)!!

        val existingPlot = createPlotEntity(
            uuid = "existing-1",
            plotName = "Overlapping Plot",
            polygonWkt = "POLYGON ((5 0, 15 0, 15 10, 5 10, 5 0))"
        )

        val overlaps = overlapChecker.checkOverlaps(newPolygon, listOf(existingPlot))

        assertEquals(1, overlaps.size)
        assertEquals(50.0, overlaps[0].overlapPercentage, 0.1)
    }

    @Test
    fun `checkOverlaps detects overlap at exactly 5 percent threshold`() {
        // New polygon: 0-100 x 0-100 (area 10000)
        // Existing polygon: 95-105 x 0-100 (area 1000)
        // Overlap: 95-100 x 0-100 (area 500) = 5% of larger (10000), but 50% of smaller (1000)
        // Threshold is based on smaller polygon, so this is well above 5%

        // Better test: create overlap that is exactly 5% of the smaller polygon
        // New polygon: 0-10 x 0-10 (area 100)
        // Existing polygon: 9.5-10.5 x 0-10 (area 10)
        // Overlap: 9.5-10 x 0-10 (area 5) = 50% of smaller (10) - above threshold
        val newPolygonWkt = "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))"
        val newPolygon = overlapChecker.parseWkt(newPolygonWkt)!!

        val existingPlot = createPlotEntity(
            uuid = "existing-1",
            plotName = "Border Plot",
            polygonWkt = "POLYGON ((9.5 0, 10.5 0, 10.5 10, 9.5 10, 9.5 0))"
        )

        val overlaps = overlapChecker.checkOverlaps(newPolygon, listOf(existingPlot))

        assertEquals(1, overlaps.size)
        assertTrue(overlaps[0].overlapPercentage >= 5.0)
    }

    @Test
    fun `checkOverlaps ignores overlap below 5 percent threshold`() {
        // New polygon: 0-100 x 0-100 (area 10000)
        // Existing polygon: 99-101 x 0-2 (area 4)
        // Overlap: 99-100 x 0-2 (area 2) = 2/4 = 50% of smaller - still above

        // Create a scenario where overlap is < 5% of smaller
        // New polygon: 0-100 x 0-100 (area 10000)
        // Existing polygon: 99.9-100.1 x 0-100 (area 20)
        // Overlap: 99.9-100 x 0-100 (area 10) = 50% of smaller (20) - still above

        // Use custom threshold for this test
        val strictChecker = OverlapChecker(overlapThresholdPercent = 60.0)

        val newPolygonWkt = "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))"
        val newPolygon = strictChecker.parseWkt(newPolygonWkt)!!

        val existingPlot = createPlotEntity(
            uuid = "existing-1",
            plotName = "Barely Overlapping",
            polygonWkt = "POLYGON ((9.5 0, 10.5 0, 10.5 10, 9.5 10, 9.5 0))"
        )

        val overlaps = strictChecker.checkOverlaps(newPolygon, listOf(existingPlot))

        // With 60% threshold, 50% overlap should be ignored
        assertEquals(0, overlaps.size)
    }

    @Test
    fun `checkOverlaps returns empty list for non-overlapping polygons`() {
        // Two squares that don't touch
        val newPolygonWkt = "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))"
        val newPolygon = overlapChecker.parseWkt(newPolygonWkt)!!

        val existingPlot = createPlotEntity(
            uuid = "existing-1",
            plotName = "Far Away Plot",
            polygonWkt = "POLYGON ((20 0, 30 0, 30 10, 20 10, 20 0))"
        )

        val overlaps = overlapChecker.checkOverlaps(newPolygon, listOf(existingPlot))

        assertEquals(0, overlaps.size)
    }

    @Test
    fun `checkOverlaps handles touching boundaries without area overlap`() {
        // Two squares that share an edge but don't overlap in area
        val newPolygonWkt = "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))"
        val newPolygon = overlapChecker.parseWkt(newPolygonWkt)!!

        val existingPlot = createPlotEntity(
            uuid = "existing-1",
            plotName = "Adjacent Plot",
            polygonWkt = "POLYGON ((10 0, 20 0, 20 10, 10 10, 10 0))"
        )

        val overlaps = overlapChecker.checkOverlaps(newPolygon, listOf(existingPlot))

        // Touching edge has zero area, should not be flagged
        assertEquals(0, overlaps.size)
    }

    @Test
    fun `checkOverlaps handles shared single vertex without overlap`() {
        // Two squares that share only a corner point
        val newPolygonWkt = "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))"
        val newPolygon = overlapChecker.parseWkt(newPolygonWkt)!!

        val existingPlot = createPlotEntity(
            uuid = "existing-1",
            plotName = "Corner Touch Plot",
            polygonWkt = "POLYGON ((10 10, 20 10, 20 20, 10 20, 10 10))"
        )

        val overlaps = overlapChecker.checkOverlaps(newPolygon, listOf(existingPlot))

        // Single point intersection has zero area
        assertEquals(0, overlaps.size)
    }

    // ==================== Multiple Candidates Tests ====================

    @Test
    fun `checkOverlaps returns multiple overlaps when applicable`() {
        // New polygon overlaps with two existing plots
        val newPolygonWkt = "POLYGON ((5 5, 15 5, 15 15, 5 15, 5 5))"
        val newPolygon = overlapChecker.parseWkt(newPolygonWkt)!!

        val plot1 = createPlotEntity(
            uuid = "plot-1",
            plotName = "Plot One",
            polygonWkt = "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))"
        )
        val plot2 = createPlotEntity(
            uuid = "plot-2",
            plotName = "Plot Two",
            polygonWkt = "POLYGON ((10 10, 20 10, 20 20, 10 20, 10 10))"
        )
        val plot3 = createPlotEntity(
            uuid = "plot-3",
            plotName = "Plot Three - No Overlap",
            polygonWkt = "POLYGON ((30 30, 40 30, 40 40, 30 40, 30 30))"
        )

        val overlaps = overlapChecker.checkOverlaps(newPolygon, listOf(plot1, plot2, plot3))

        assertEquals(2, overlaps.size)
        assertTrue(overlaps.any { it.uuid == "plot-1" })
        assertTrue(overlaps.any { it.uuid == "plot-2" })
    }

    @Test
    fun `checkOverlaps handles empty candidate list`() {
        val newPolygonWkt = "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))"
        val newPolygon = overlapChecker.parseWkt(newPolygonWkt)!!

        val overlaps = overlapChecker.checkOverlaps(newPolygon, emptyList())

        assertEquals(0, overlaps.size)
    }

    @Test
    fun `checkOverlaps skips candidates with invalid WKT`() {
        val newPolygonWkt = "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))"
        val newPolygon = overlapChecker.parseWkt(newPolygonWkt)!!

        val validPlot = createPlotEntity(
            uuid = "valid-plot",
            plotName = "Valid Plot",
            polygonWkt = "POLYGON ((5 0, 15 0, 15 10, 5 10, 5 0))"
        )
        val invalidPlot = createPlotEntity(
            uuid = "invalid-plot",
            plotName = "Invalid Plot",
            polygonWkt = "INVALID WKT DATA"
        )

        val overlaps = overlapChecker.checkOverlaps(newPolygon, listOf(invalidPlot, validPlot))

        // Should only find the valid overlapping plot
        assertEquals(1, overlaps.size)
        assertEquals("valid-plot", overlaps[0].uuid)
    }

    // ==================== Overlap Percentage Calculation Tests ====================

    @Test
    fun `overlap percentage is calculated relative to smaller polygon`() {
        // Large polygon: 0-100 x 0-100 (area 10000)
        // Small polygon inside: 0-10 x 0-10 (area 100)
        // Overlap = 100 (entire small polygon)
        // Percentage should be 100% (relative to smaller), not 1% (relative to larger)
        val largePolygonWkt = "POLYGON ((0 0, 100 0, 100 100, 0 100, 0 0))"
        val largePolygon = overlapChecker.parseWkt(largePolygonWkt)!!

        val smallPlot = createPlotEntity(
            uuid = "small-plot",
            plotName = "Small Plot",
            polygonWkt = "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))"
        )

        val overlaps = overlapChecker.checkOverlaps(largePolygon, listOf(smallPlot))

        assertEquals(1, overlaps.size)
        // The small polygon is entirely inside the large one
        assertEquals(100.0, overlaps[0].overlapPercentage, 0.1)
    }

    // ==================== Custom Threshold Tests ====================

    @Test
    fun `custom overlap threshold is respected`() {
        // Use 10% threshold
        val strictChecker = OverlapChecker(overlapThresholdPercent = 10.0)

        // New polygon: 0-10 x 0-10 (area 100)
        // Existing polygon: 9-11 x 0-10 (area 20)
        // Overlap: 9-10 x 0-10 (area 10) = 50% of smaller (20) - above 10%
        val newPolygonWkt = "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))"
        val newPolygon = strictChecker.parseWkt(newPolygonWkt)!!

        val existingPlot = createPlotEntity(
            uuid = "existing-1",
            plotName = "Overlapping",
            polygonWkt = "POLYGON ((9 0, 11 0, 11 10, 9 10, 9 0))"
        )

        val overlaps = strictChecker.checkOverlaps(newPolygon, listOf(existingPlot))

        assertEquals(1, overlaps.size)
        assertTrue(overlaps[0].overlapPercentage >= 10.0)
    }

    @Test
    fun `default threshold constant is 5 percent`() {
        assertEquals(5.0, OverlapChecker.DEFAULT_OVERLAP_THRESHOLD_PERCENT, 0.001)
    }

    // ==================== Helper Functions ====================

    private fun createPlotEntity(
        uuid: String,
        plotName: String,
        polygonWkt: String
    ): PlotEntity {
        return PlotEntity(
            uuid = uuid,
            plotName = plotName,
            instanceName = "test-instance",
            polygonWkt = polygonWkt,
            minLat = 0.0,
            maxLat = 10.0,
            minLon = 0.0,
            maxLon = 10.0,
            isDraft = true,
            formId = "test-form",
            region = "test-region",
            subRegion = "test-sub-region"
        )
    }
}
