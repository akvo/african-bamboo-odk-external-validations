package org.akvo.afribamodkvalidator.data.repository

import org.akvo.afribamodkvalidator.data.entity.SubmissionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PlotExtractor.
 *
 * Tests cover:
 * - Successful extraction with valid data and dynamic polygon fields
 * - Handling of missing required fields
 * - Polygon field priority (first match wins)
 * - Plot name uses instanceName → _id fallback
 * - Polygon parsing failures
 * - Edge cases
 */
class PlotExtractorTest {

    private lateinit var extractor: PlotExtractor

    // Valid ODK polygon data (a square approx 100m x 100m)
    private val validPolygonData = "9.0 38.0 0 0; 9.001 38.0 0 0; 9.001 38.001 0 0; 9.0 38.001 0 0; 9.0 38.0 0 0"

    // Default polygon fields for most tests
    private val defaultPolygonFields = listOf(
        "boundary_mapping/Open_Area_GeoMapping",
        "Open_Area_GeoMapping",
        "manual_boundary"
    )

    @Before
    fun setup() {
        extractor = PlotExtractor()
    }

    private fun createSubmission(
        uuid: String = "test-uuid",
        assetUid: String = "test-form",
        id: String = "123",
        instanceName: String? = "test-instance",
        rawData: String
    ) = SubmissionEntity(
        _uuid = uuid,
        assetUid = assetUid,
        _id = id,
        submissionTime = System.currentTimeMillis(),
        submittedBy = "testuser",
        instanceName = instanceName,
        rawData = rawData,
        systemData = null
    )

    // ==================== Successful Extraction Tests ====================

    @Test
    fun `extract plot with all valid fields succeeds`() {
        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "$validPolygonData",
                "woreda": "Woreda-05",
                "kebele": "Kebele-012"
            }
        """.trimIndent()

        val result = extractor.extractPlot(
            createSubmission(
                uuid = "submission-uuid",
                assetUid = "form-123",
                instanceName = "instance-456",
                rawData = rawData
            ),
            defaultPolygonFields
        )

        assertNotNull(result)
        assertEquals("instance-456", result?.plotName)
        assertEquals("instance-456", result?.instanceName)
        assertEquals("Woreda-05", result?.region)
        assertEquals("Kebele-012", result?.subRegion)
        assertEquals("submission-uuid", result?.submissionUuid)
        assertEquals("form-123", result?.formId)
        assertFalse(result?.isDraft ?: true)
        assertTrue(result?.polygonWkt?.startsWith("POLYGON") ?: false)
    }

    // ==================== Plot Name Tests ====================

    @Test
    fun `plot name uses instanceName when available`() {
        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "$validPolygonData",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(
            createSubmission(instanceName = "enum_006-ET04-2026-03-17", rawData = rawData),
            defaultPolygonFields
        )

        assertNotNull(result)
        assertEquals("enum_006-ET04-2026-03-17", result?.plotName)
    }

    @Test
    fun `plot name falls back to _id when instanceName is null`() {
        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "$validPolygonData",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(
            createSubmission(instanceName = null, id = "757910942", rawData = rawData),
            defaultPolygonFields
        )

        assertNotNull(result)
        assertEquals("757910942", result?.plotName)
    }

    @Test
    fun `instanceName used for instanceName field, uuid fallback when null`() {
        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "$validPolygonData",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(
            createSubmission(uuid = "submission-uuid-123", instanceName = null, rawData = rawData),
            defaultPolygonFields
        )

        assertNotNull(result)
        assertEquals("submission-uuid-123", result?.instanceName)
    }

    // ==================== Dynamic Polygon Field Tests ====================

    @Test
    fun `uses dynamic polygon fields from parameter`() {
        val rawData = """
            {
                "validate_polygon": "$validPolygonData",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val customFields = listOf("validate_polygon")
        val result = extractor.extractPlot(createSubmission(rawData = rawData), customFields)

        assertNotNull("Should find polygon using dynamic field name", result)
    }

    @Test
    fun `polygon field priority - uses first matching field`() {
        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "$validPolygonData",
                "Open_Area_GeoMapping": "invalid polygon data",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData), defaultPolygonFields)

        assertNotNull("Should use first polygon field", result)
    }

    @Test
    fun `polygon field priority - falls back to second field when first is missing`() {
        val rawData = """
            {
                "Open_Area_GeoMapping": "$validPolygonData",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData), defaultPolygonFields)

        assertNotNull("Should fall back to second polygon field", result)
    }

    @Test
    fun `polygon field skips empty string values`() {
        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "",
                "Open_Area_GeoMapping": "$validPolygonData",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData), defaultPolygonFields)

        assertNotNull("Should skip empty polygon field", result)
    }

    @Test
    fun `returns null when no polygon field matches`() {
        val rawData = """
            {
                "some_other_field": "$validPolygonData",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData), defaultPolygonFields)

        assertNull("Should return null when no polygon field matches", result)
    }

    @Test
    fun `returns null when polygon fields list is empty`() {
        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "$validPolygonData",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData), emptyList())

        assertNull("Should return null when polygon fields list is empty", result)
    }

    // ==================== Region Tests ====================

    @Test
    fun `handles missing region gracefully`() {
        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "$validPolygonData",
                "kebele": "test-kebele"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData), defaultPolygonFields)

        assertNotNull(result)
        assertEquals("", result?.region)
        assertEquals("test-kebele", result?.subRegion)
    }

    // ==================== Parsing Failure Tests ====================

    @Test
    fun `returns null for invalid polygon format`() {
        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "not a valid polygon",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData), defaultPolygonFields)

        assertNull("Should return null for invalid polygon format", result)
    }

    @Test
    fun `returns null for invalid JSON`() {
        val result = extractor.extractPlot(
            createSubmission(rawData = "not valid json"),
            defaultPolygonFields
        )

        assertNull("Should return null for invalid JSON", result)
    }

    @Test
    fun `supports WKT polygon format`() {
        val wktPolygon = "POLYGON ((38.0 9.0, 38.001 9.0, 38.001 9.001, 38.0 9.001, 38.0 9.0))"

        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "$wktPolygon",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData), defaultPolygonFields)

        assertNotNull("Should support WKT polygon format", result)
    }

    @Test
    fun `generates unique UUID for each extracted plot`() {
        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "$validPolygonData",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result1 = extractor.extractPlot(createSubmission(uuid = "sub-1", rawData = rawData), defaultPolygonFields)
        val result2 = extractor.extractPlot(createSubmission(uuid = "sub-2", rawData = rawData), defaultPolygonFields)

        assertNotNull(result1)
        assertNotNull(result2)
        assertTrue("UUIDs should be different", result1?.uuid != result2?.uuid)
    }

    @Test
    fun `computes correct bounding box`() {
        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "$validPolygonData",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData), defaultPolygonFields)

        assertNotNull(result)
        assertTrue(result?.minLat ?: 0.0 >= 9.0)
        assertTrue(result?.maxLat ?: 0.0 <= 9.002)
        assertTrue(result?.minLon ?: 0.0 >= 38.0)
        assertTrue(result?.maxLon ?: 0.0 <= 38.002)
    }
}
