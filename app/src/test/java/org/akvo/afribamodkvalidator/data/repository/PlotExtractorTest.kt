package org.akvo.afribamodkvalidator.data.repository

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import org.akvo.afribamodkvalidator.data.entity.SubmissionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException

/**
 * Unit tests for PlotExtractor.
 *
 * Tests cover:
 * - Successful extraction with valid data
 * - Handling of missing required fields (polygon, region, etc.)
 * - Config loading and fallback to defaults
 * - Polygon parsing failures
 * - Different field path priorities
 * - Edge cases like empty plot names
 */
class PlotExtractorTest {

    private lateinit var context: Context
    private lateinit var assetManager: AssetManager

    // Valid ODK polygon data (a square approx 100m x 100m)
    private val validPolygonData = "9.0 38.0 0 0; 9.001 38.0 0 0; 9.001 38.001 0 0; 9.0 38.001 0 0; 9.0 38.0 0 0"

    // Default test config matching production defaults
    private val testConfigJson = """
        {
            "polygonFields": ["boundary_mapping/Open_Area_GeoMapping", "Open_Area_GeoMapping", "manual_boundary"],
            "plotNameFields": ["First_Name", "Father_s_Name", "Grandfather_s_Name"],
            "regionField": "woreda",
            "subRegionField": "kebele"
        }
    """.trimIndent()

    @Before
    fun setup() {
        assetManager = mockk()
        context = mockk()
        every { context.assets } returns assetManager
    }

    private fun setupConfigLoading(configJson: String = testConfigJson) {
        every { assetManager.open("plot_extraction_config.json") } returns
                ByteArrayInputStream(configJson.toByteArray())
    }

    private fun setupConfigLoadingFailure() {
        every { assetManager.open("plot_extraction_config.json") } throws
                FileNotFoundException("Config not found")
    }

    private fun createSubmission(
        uuid: String = "test-uuid",
        assetUid: String = "test-form",
        instanceName: String? = "test-instance",
        rawData: String
    ) = SubmissionEntity(
        _uuid = uuid,
        assetUid = assetUid,
        _id = "123",
        submissionTime = System.currentTimeMillis(),
        submittedBy = "testuser",
        instanceName = instanceName,
        rawData = rawData,
        systemData = null
    )

    // ==================== Config Loading Tests ====================

    @Test
    fun `config loading success logs and uses loaded config`() {
        setupConfigLoading()

        val extractor = PlotExtractor(context)

        // Verify extractor was created (config loaded successfully)
        assertNotNull(extractor)
    }

    @Test
    fun `config loading failure falls back to defaults`() {
        setupConfigLoadingFailure()

        val extractor = PlotExtractor(context)

        // Should still create extractor with default config
        assertNotNull(extractor)

        // Test that default polygon fields work
        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "$validPolygonData",
                "First_Name": "John",
                "woreda": "test-woreda",
                "kebele": "test-kebele"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData))
        assertNotNull("Should extract plot using default config", result)
    }

    @Test
    fun `custom config with different field names works`() {
        val customConfig = """
            {
                "polygonFields": ["custom_polygon_field"],
                "plotNameFields": ["farmer_name"],
                "regionField": "district",
                "subRegionField": "village"
            }
        """.trimIndent()
        setupConfigLoading(customConfig)

        val extractor = PlotExtractor(context)

        val rawData = """
            {
                "custom_polygon_field": "$validPolygonData",
                "farmer_name": "Custom Name",
                "district": "Test District",
                "village": "Test Village"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData))

        assertNotNull(result)
        assertEquals("Custom Name", result?.plotName)
        assertEquals("Test District", result?.region)
        assertEquals("Test Village", result?.subRegion)
    }

    // ==================== Successful Extraction Tests ====================

    @Test
    fun `extract plot with all valid fields succeeds`() {
        setupConfigLoading()
        val extractor = PlotExtractor(context)

        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "$validPolygonData",
                "First_Name": "John",
                "Father_s_Name": "Michael",
                "Grandfather_s_Name": "Robert",
                "woreda": "Woreda-05",
                "kebele": "Kebele-012"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(
            uuid = "submission-uuid",
            assetUid = "form-123",
            instanceName = "instance-456",
            rawData = rawData
        ))

        assertNotNull(result)
        assertEquals("John Michael Robert", result?.plotName)
        assertEquals("instance-456", result?.instanceName)
        assertEquals("Woreda-05", result?.region)
        assertEquals("Kebele-012", result?.subRegion)
        assertEquals("submission-uuid", result?.submissionUuid)
        assertEquals("form-123", result?.formId)
        assertFalse(result?.isDraft ?: true)
        assertNotNull(result?.polygonWkt)
        assertTrue(result?.polygonWkt?.startsWith("POLYGON") ?: false)
    }

    @Test
    fun `extract plot uses submission uuid when instanceName is null`() {
        setupConfigLoading()
        val extractor = PlotExtractor(context)

        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "$validPolygonData",
                "First_Name": "John",
                "woreda": "test-woreda",
                "kebele": "test-kebele"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(
            uuid = "submission-uuid-123",
            instanceName = null,
            rawData = rawData
        ))

        assertNotNull(result)
        assertEquals("submission-uuid-123", result?.instanceName)
    }

    @Test
    fun `extract plot computes correct bounding box`() {
        setupConfigLoading()
        val extractor = PlotExtractor(context)

        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "$validPolygonData",
                "First_Name": "Test",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData))

        assertNotNull(result)
        // Verify bounding box values are reasonable
        assertTrue(result?.minLat ?: 0.0 >= 9.0)
        assertTrue(result?.maxLat ?: 0.0 <= 9.002)
        assertTrue(result?.minLon ?: 0.0 >= 38.0)
        assertTrue(result?.maxLon ?: 0.0 <= 38.002)
    }

    // ==================== Field Path Priority Tests ====================

    @Test
    fun `polygon field priority - uses first matching field`() {
        setupConfigLoading()
        val extractor = PlotExtractor(context)

        // Has both fields, should use first one (boundary_mapping/Open_Area_GeoMapping)
        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "$validPolygonData",
                "Open_Area_GeoMapping": "invalid polygon data that would fail",
                "First_Name": "Test",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData))

        assertNotNull("Should use first polygon field", result)
    }

    @Test
    fun `polygon field priority - falls back to second field when first is missing`() {
        setupConfigLoading()
        val extractor = PlotExtractor(context)

        // Only has second polygon field
        val rawData = """
            {
                "Open_Area_GeoMapping": "$validPolygonData",
                "First_Name": "Test",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData))

        assertNotNull("Should fall back to second polygon field", result)
    }

    @Test
    fun `polygon field priority - falls back to third field`() {
        setupConfigLoading()
        val extractor = PlotExtractor(context)

        // Only has third polygon field (manual_boundary)
        val rawData = """
            {
                "manual_boundary": "$validPolygonData",
                "First_Name": "Test",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData))

        assertNotNull("Should fall back to third polygon field", result)
    }

    @Test
    fun `polygon field skips empty string values`() {
        setupConfigLoading()
        val extractor = PlotExtractor(context)

        // First field is empty, should use second
        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "",
                "Open_Area_GeoMapping": "$validPolygonData",
                "First_Name": "Test",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData))

        assertNotNull("Should skip empty polygon field", result)
    }

    @Test
    fun `polygon field skips whitespace-only values`() {
        setupConfigLoading()
        val extractor = PlotExtractor(context)

        // First field is whitespace, should use second
        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "   ",
                "Open_Area_GeoMapping": "$validPolygonData",
                "First_Name": "Test",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData))

        assertNotNull("Should skip whitespace-only polygon field", result)
    }

    // ==================== Missing Required Fields Tests ====================

    @Test
    fun `returns null when no polygon field is present`() {
        setupConfigLoading()
        val extractor = PlotExtractor(context)

        val rawData = """
            {
                "First_Name": "John",
                "woreda": "test-woreda",
                "kebele": "test-kebele"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData))

        assertNull("Should return null when polygon is missing", result)
    }

    @Test
    fun `returns null when rawData is not valid JSON`() {
        setupConfigLoading()
        val extractor = PlotExtractor(context)

        val result = extractor.extractPlot(createSubmission(rawData = "not valid json"))

        assertNull("Should return null for invalid JSON", result)
    }

    @Test
    fun `returns null when rawData is empty string`() {
        setupConfigLoading()
        val extractor = PlotExtractor(context)

        val result = extractor.extractPlot(createSubmission(rawData = ""))

        assertNull("Should return null for empty rawData", result)
    }

    @Test
    fun `returns null when rawData is JSON array instead of object`() {
        setupConfigLoading()
        val extractor = PlotExtractor(context)

        val result = extractor.extractPlot(createSubmission(rawData = "[1, 2, 3]"))

        assertNull("Should return null for JSON array", result)
    }

    @Test
    fun `handles missing region gracefully - uses empty string`() {
        setupConfigLoading()
        val extractor = PlotExtractor(context)

        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "$validPolygonData",
                "First_Name": "Test",
                "kebele": "test-kebele"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData))

        assertNotNull(result)
        assertEquals("", result?.region)
        assertEquals("test-kebele", result?.subRegion)
    }

    @Test
    fun `handles missing subRegion gracefully - uses empty string`() {
        setupConfigLoading()
        val extractor = PlotExtractor(context)

        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "$validPolygonData",
                "First_Name": "Test",
                "woreda": "test-woreda"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData))

        assertNotNull(result)
        assertEquals("test-woreda", result?.region)
        assertEquals("", result?.subRegion)
    }

    // ==================== Plot Name Tests ====================

    @Test
    fun `plot name combines all available name fields`() {
        setupConfigLoading()
        val extractor = PlotExtractor(context)

        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "$validPolygonData",
                "First_Name": "John",
                "Father_s_Name": "Michael",
                "Grandfather_s_Name": "Robert",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData))

        assertEquals("John Michael Robert", result?.plotName)
    }

    @Test
    fun `plot name handles partial name fields`() {
        setupConfigLoading()
        val extractor = PlotExtractor(context)

        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "$validPolygonData",
                "First_Name": "John",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData))

        assertEquals("John", result?.plotName)
    }

    @Test
    fun `plot name defaults to Unknown when all name fields are missing`() {
        setupConfigLoading()
        val extractor = PlotExtractor(context)

        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "$validPolygonData",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData))

        assertEquals("Unknown", result?.plotName)
    }

    @Test
    fun `plot name defaults to Unknown when name fields are empty strings`() {
        setupConfigLoading()
        val extractor = PlotExtractor(context)

        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "$validPolygonData",
                "First_Name": "",
                "Father_s_Name": "   ",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData))

        assertEquals("Unknown", result?.plotName)
    }

    @Test
    fun `plot name skips blank values in middle`() {
        setupConfigLoading()
        val extractor = PlotExtractor(context)

        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "$validPolygonData",
                "First_Name": "John",
                "Father_s_Name": "",
                "Grandfather_s_Name": "Robert",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData))

        assertEquals("John Robert", result?.plotName)
    }

    // ==================== Polygon Parsing Failure Tests ====================

    @Test
    fun `returns null for invalid polygon format`() {
        setupConfigLoading()
        val extractor = PlotExtractor(context)

        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "not a valid polygon",
                "First_Name": "Test",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData))

        assertNull("Should return null for invalid polygon format", result)
    }

    @Test
    fun `returns null for polygon with too few points`() {
        setupConfigLoading()
        val extractor = PlotExtractor(context)

        // Only 2 points - not enough for a polygon
        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "9.0 38.0 0 0; 9.001 38.0 0 0",
                "First_Name": "Test",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData))

        assertNull("Should return null for polygon with too few points", result)
    }

    @Test
    fun `returns null for polygon with invalid coordinates`() {
        setupConfigLoading()
        val extractor = PlotExtractor(context)

        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "abc def 0 0; xyz 123 0 0; 9.0 38.0 0 0",
                "First_Name": "Test",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData))

        assertNull("Should return null for invalid coordinates", result)
    }

    @Test
    fun `self-intersecting polygon parses but should be validated separately`() {
        setupConfigLoading()
        val extractor = PlotExtractor(context)

        // A large bowtie/figure-8 pattern (self-intersecting) using WKT format
        // Note: PlotExtractor only parses polygons, it doesn't validate for self-intersection.
        // Self-intersection validation should be done separately using PolygonValidator.validate()
        val bowtieWkt = "POLYGON ((0 0, 100 100, 100 0, 0 100, 0 0))"

        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "$bowtieWkt",
                "First_Name": "Test",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData))

        // PlotExtractor parses but doesn't validate geometry - self-intersecting polygons
        // are extracted. Validation for self-intersection is a separate concern.
        assertNotNull("Self-intersecting polygon is parsed (validation is separate)", result)
        assertNotNull(result?.polygonWkt)
    }

    // ==================== WKT Format Tests ====================

    @Test
    fun `supports WKT polygon format`() {
        setupConfigLoading()
        val extractor = PlotExtractor(context)

        val wktPolygon = "POLYGON ((38.0 9.0, 38.001 9.0, 38.001 9.001, 38.0 9.001, 38.0 9.0))"

        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "$wktPolygon",
                "First_Name": "Test",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = rawData))

        assertNotNull("Should support WKT polygon format", result)
        assertTrue(result?.polygonWkt?.startsWith("POLYGON") ?: false)
    }

    // ==================== Generated UUID Tests ====================

    @Test
    fun `generates unique UUID for each extracted plot`() {
        setupConfigLoading()
        val extractor = PlotExtractor(context)

        val rawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": "$validPolygonData",
                "First_Name": "Test",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result1 = extractor.extractPlot(createSubmission(uuid = "sub-1", rawData = rawData))
        val result2 = extractor.extractPlot(createSubmission(uuid = "sub-2", rawData = rawData))

        assertNotNull(result1)
        assertNotNull(result2)
        assertTrue("UUIDs should be different", result1?.uuid != result2?.uuid)
    }

    // ==================== Exception Handling Tests ====================

    @Test
    fun `handles exceptions gracefully and returns null`() {
        setupConfigLoading()
        val extractor = PlotExtractor(context)

        // JSON with nested structure that might cause parsing issues
        val problematicRawData = """
            {
                "boundary_mapping/Open_Area_GeoMapping": {"nested": "object"},
                "First_Name": "Test",
                "woreda": "test",
                "kebele": "test"
            }
        """.trimIndent()

        val result = extractor.extractPlot(createSubmission(rawData = problematicRawData))

        // Should handle gracefully without throwing
        assertNull("Should return null for nested polygon object", result)
    }
}
