package org.akvo.afribamodkvalidator.data.repository

import io.mockk.mockk
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for dynamic polygon field discovery from Kobo asset detail.
 */
class PolygonFieldDiscoveryTest {

    private val repository = KoboRepository(
        apiService = mockk(relaxed = true),
        submissionDao = mockk(relaxed = true),
        formMetadataDao = mockk(relaxed = true),
        plotDao = mockk(relaxed = true),
        plotExtractor = mockk(relaxed = true),
        plotWarningDao = mockk(relaxed = true)
    )

    private fun surveyItem(type: String, name: String, xpath: String? = null): JsonObject {
        val fields = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
            "type" to JsonPrimitive(type),
            "name" to JsonPrimitive(name)
        )
        if (xpath != null) {
            fields["\$xpath"] = JsonPrimitive(xpath)
        }
        return JsonObject(fields)
    }

    private fun assetDetail(vararg items: JsonObject): JsonObject {
        return JsonObject(mapOf(
            "content" to JsonObject(mapOf(
                "survey" to JsonArray(items.toList())
            ))
        ))
    }

    @Test
    fun `finds geoshape fields from survey`() {
        val detail = assetDetail(
            surveyItem("text", "first_name"),
            surveyItem("geoshape", "manual_boundary", "boundary_mapping/manual_boundary"),
            surveyItem("select_one regions", "region")
        )

        val result = repository.extractPolygonFieldPaths(detail)

        assertEquals(1, result.size)
        assertEquals("boundary_mapping/manual_boundary", result[0])
    }

    @Test
    fun `finds geotrace fields from survey`() {
        val detail = assetDetail(
            surveyItem("geotrace", "walking_path", "consent_group/walking_path")
        )

        val result = repository.extractPolygonFieldPaths(detail)

        assertEquals(1, result.size)
        assertEquals("consent_group/walking_path", result[0])
    }

    @Test
    fun `finds multiple geo fields`() {
        val detail = assetDetail(
            surveyItem("geoshape", "plot_boundary", "boundary_mapping/Open_Area_GeoMapping"),
            surveyItem("text", "name"),
            surveyItem("geoshape", "manual_boundary", "boundary_mapping/manual_boundary"),
            surveyItem("geotrace", "trace_path", "trace/path")
        )

        val result = repository.extractPolygonFieldPaths(detail)

        assertEquals(3, result.size)
        assertEquals("boundary_mapping/Open_Area_GeoMapping", result[0])
        assertEquals("boundary_mapping/manual_boundary", result[1])
        assertEquals("trace/path", result[2])
    }

    @Test
    fun `returns empty list when no geo fields exist`() {
        val detail = assetDetail(
            surveyItem("text", "first_name"),
            surveyItem("integer", "age"),
            surveyItem("select_one regions", "region")
        )

        val result = repository.extractPolygonFieldPaths(detail)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `prefers xpath over name`() {
        val detail = assetDetail(
            surveyItem("geoshape", "manual_boundary", "boundary_mapping/manual_boundary")
        )

        val result = repository.extractPolygonFieldPaths(detail)

        assertEquals("boundary_mapping/manual_boundary", result[0])
    }

    @Test
    fun `falls back to name when xpath is missing`() {
        val detail = assetDetail(
            surveyItem("geoshape", "validate_polygon")
        )

        val result = repository.extractPolygonFieldPaths(detail)

        assertEquals(1, result.size)
        assertEquals("validate_polygon", result[0])
    }

    @Test
    fun `handles missing content gracefully`() {
        val detail = JsonObject(emptyMap())

        val result = repository.extractPolygonFieldPaths(detail)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `handles missing survey gracefully`() {
        val detail = JsonObject(mapOf(
            "content" to JsonObject(emptyMap())
        ))

        val result = repository.extractPolygonFieldPaths(detail)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `ignores non-geo field types`() {
        val detail = assetDetail(
            surveyItem("text", "name"),
            surveyItem("integer", "age"),
            surveyItem("calculate", "full_name"),
            surveyItem("select_one", "region"),
            surveyItem("geopoint", "location"),
            surveyItem("image", "photo")
        )

        val result = repository.extractPolygonFieldPaths(detail)

        assertTrue("geopoint and other types should not be included", result.isEmpty())
    }

    @Test
    fun `handles real-world Kobo form with nested geoshape`() {
        val detail = assetDetail(
            surveyItem("begin_group", "consent_group"),
            surveyItem("text", "First_Name", "consent_group/consented/First_Name"),
            surveyItem("geoshape", "Open_Area_GeoMapping", "consent_group/consented/boundary_mapping/Open_Area_GeoMapping"),
            surveyItem("end_group", "consent_group")
        )

        val result = repository.extractPolygonFieldPaths(detail)

        assertEquals(1, result.size)
        assertEquals("consent_group/consented/boundary_mapping/Open_Area_GeoMapping", result[0])
    }
}
