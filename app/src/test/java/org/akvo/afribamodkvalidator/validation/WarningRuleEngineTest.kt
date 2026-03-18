package org.akvo.afribamodkvalidator.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WarningRuleEngineTest {

    // --- W1: GPS Accuracy ---

    @Test
    fun W1_no_warning_when_average_accuracy_within_threshold() {
        val coords = listOf(
            GeoCoordinate(7.0, 38.0, 0.0, 10.0),
            GeoCoordinate(7.001, 38.0, 0.0, 12.0),
            GeoCoordinate(7.001, 38.001, 0.0, 14.0),
            GeoCoordinate(7.0, 38.001, 0.0, 10.0),
            GeoCoordinate(7.0, 38.0, 0.0, 10.0)
        )
        assertNull(WarningRuleEngine.checkGpsAccuracy(coords))
    }

    @Test
    fun W1_warning_when_average_accuracy_exceeds_threshold() {
        val coords = listOf(
            GeoCoordinate(7.0, 38.0, 0.0, 18.0),
            GeoCoordinate(7.001, 38.0, 0.0, 20.0),
            GeoCoordinate(7.001, 38.001, 0.0, 17.0),
            GeoCoordinate(7.0, 38.001, 0.0, 19.0),
            GeoCoordinate(7.0, 38.0, 0.0, 18.0)
        )
        val warning = WarningRuleEngine.checkGpsAccuracy(coords)
        assertNotNull(warning)
        assertEquals(WarningType.GPS_ACCURACY_LOW, warning!!.type)
        assertTrue(warning.value > 15.0)
    }

    @Test
    fun W1_skips_zero_accuracy_values() {
        val coords = listOf(
            GeoCoordinate(7.0, 38.0, 0.0, 0.0),
            GeoCoordinate(7.001, 38.0, 0.0, 10.0),
            GeoCoordinate(7.001, 38.001, 0.0, 0.0),
            GeoCoordinate(7.0, 38.001, 0.0, 12.0),
            GeoCoordinate(7.0, 38.0, 0.0, 0.0)
        )
        assertNull(WarningRuleEngine.checkGpsAccuracy(coords))
    }

    @Test
    fun W1_no_warning_when_all_accuracy_values_are_zero() {
        val coords = listOf(
            GeoCoordinate(7.0, 38.0, 0.0, 0.0),
            GeoCoordinate(7.001, 38.0, 0.0, 0.0),
            GeoCoordinate(7.001, 38.001, 0.0, 0.0),
            GeoCoordinate(7.0, 38.0, 0.0, 0.0)
        )
        assertNull(WarningRuleEngine.checkGpsAccuracy(coords))
    }

    @Test
    fun W1_mixed_zero_and_high_accuracy_triggers_warning() {
        val coords = listOf(
            GeoCoordinate(7.0, 38.0, 0.0, 0.0),
            GeoCoordinate(7.001, 38.0, 0.0, 20.0),
            GeoCoordinate(7.001, 38.001, 0.0, 0.0),
            GeoCoordinate(7.0, 38.001, 0.0, 18.0),
            GeoCoordinate(7.0, 38.0, 0.0, 0.0)
        )
        val warning = WarningRuleEngine.checkGpsAccuracy(coords)
        assertNotNull(warning)
        assertEquals(WarningType.GPS_ACCURACY_LOW, warning!!.type)
    }

    // --- W2: Point Gaps ---

    @Test
    fun W2_no_warning_when_all_gaps_under_threshold() {
        val coords = listOf(
            GeoCoordinate(7.0, 38.0),
            GeoCoordinate(7.00027, 38.0),
            GeoCoordinate(7.00027, 38.00027),
            GeoCoordinate(7.0, 38.00027),
            GeoCoordinate(7.0, 38.0)
        )
        val warnings = WarningRuleEngine.checkPointGaps(coords)
        assertTrue("Expected no gap warnings", warnings.isEmpty())
    }

    @Test
    fun W2_warning_when_gap_exceeds_threshold() {
        val coords = listOf(
            GeoCoordinate(7.0, 38.0),
            GeoCoordinate(7.00027, 38.0),
            GeoCoordinate(7.0012, 38.0),
            GeoCoordinate(7.0012, 38.00027),
            GeoCoordinate(7.0, 38.0)
        )
        val warnings = WarningRuleEngine.checkPointGaps(coords)
        assertTrue("Expected at least one gap warning", warnings.isNotEmpty())
        assertTrue(warnings.any { it.type == WarningType.POINT_GAP_LARGE })
    }

    @Test
    fun W2_message_includes_segment_info() {
        val coords = listOf(
            GeoCoordinate(7.0, 38.0),
            GeoCoordinate(7.001, 38.0),
            GeoCoordinate(7.001, 38.001),
            GeoCoordinate(7.0, 38.0)
        )
        val warnings = WarningRuleEngine.checkPointGaps(coords)
        assertTrue(warnings.isNotEmpty())
        assertTrue(warnings.first().message.contains("1-2"))
    }

    @Test
    fun W2_single_point_returns_empty() {
        val coords = listOf(GeoCoordinate(7.0, 38.0))
        assertTrue(WarningRuleEngine.checkPointGaps(coords).isEmpty())
    }

    // --- W3: Uneven Spacing ---

    @Test
    fun W3_no_warning_for_evenly_spaced_points() {
        val coords = listOf(
            GeoCoordinate(7.0, 38.0),
            GeoCoordinate(7.001, 38.0),
            GeoCoordinate(7.001, 38.001),
            GeoCoordinate(7.0, 38.001),
            GeoCoordinate(7.0, 38.0)
        )
        assertNull(WarningRuleEngine.checkUnevenSpacing(coords))
    }

    @Test
    fun W3_warning_for_very_uneven_spacing() {
        val coords = listOf(
            GeoCoordinate(7.0, 38.0),
            GeoCoordinate(7.00001, 38.0),
            GeoCoordinate(7.005, 38.0),
            GeoCoordinate(7.0, 38.0)
        )
        val warning = WarningRuleEngine.checkUnevenSpacing(coords)
        assertNotNull(warning)
        assertEquals(WarningType.UNEVEN_SPACING, warning!!.type)
        assertTrue(warning.value > 0.5)
    }

    @Test
    fun W3_handles_fewer_than_3_points_gracefully() {
        val coords = listOf(
            GeoCoordinate(7.0, 38.0),
            GeoCoordinate(7.001, 38.0)
        )
        assertNull(WarningRuleEngine.checkUnevenSpacing(coords))
    }

    // --- W4: Area Too Large ---

    @Test
    fun W4_no_warning_when_area_within_threshold() {
        assertNull(WarningRuleEngine.checkAreaTooLarge(15.0))
    }

    @Test
    fun W4_no_warning_at_exactly_threshold() {
        assertNull(WarningRuleEngine.checkAreaTooLarge(20.0))
    }

    @Test
    fun W4_warning_when_area_exceeds_threshold() {
        val warning = WarningRuleEngine.checkAreaTooLarge(25.1)
        assertNotNull(warning)
        assertEquals(WarningType.AREA_TOO_LARGE, warning!!.type)
        assertEquals(25.1, warning.value, 0.01)
    }

    // --- W5: Low Vertex Count ---

    @Test
    fun W5_no_warning_for_5_vertices_below_range() {
        val closedCoords = listOf(
            GeoCoordinate(7.0, 38.0),
            GeoCoordinate(7.001, 38.0),
            GeoCoordinate(7.002, 38.001),
            GeoCoordinate(7.001, 38.002),
            GeoCoordinate(7.0, 38.001),
            GeoCoordinate(7.0, 38.0)
        )
        assertNull(WarningRuleEngine.checkLowVertexCount(closedCoords))
    }

    @Test
    fun W5_warning_for_6_vertices_lower_boundary() {
        val coords = listOf(
            GeoCoordinate(7.0, 38.0),
            GeoCoordinate(7.001, 38.0),
            GeoCoordinate(7.002, 38.0),
            GeoCoordinate(7.002, 38.001),
            GeoCoordinate(7.001, 38.001),
            GeoCoordinate(7.0, 38.001),
            GeoCoordinate(7.0, 38.0)
        )
        val warning = WarningRuleEngine.checkLowVertexCount(coords)
        assertNotNull(warning)
        assertEquals(WarningType.LOW_VERTEX_COUNT, warning!!.type)
        assertEquals(6.0, warning.value, 0.01)
    }

    @Test
    fun W5_warning_for_10_vertices_upper_boundary() {
        val coords = (0 until 10).map {
            GeoCoordinate(7.0 + it * 0.001, 38.0 + (it % 2) * 0.001)
        } + GeoCoordinate(7.0, 38.0)
        val warning = WarningRuleEngine.checkLowVertexCount(coords)
        assertNotNull(warning)
        assertEquals(10.0, warning!!.value, 0.01)
    }

    @Test
    fun W5_no_warning_for_11_vertices_above_range() {
        val coords = (0 until 11).map {
            GeoCoordinate(7.0 + it * 0.001, 38.0 + (it % 2) * 0.001)
        } + GeoCoordinate(7.0, 38.0)
        assertNull(WarningRuleEngine.checkLowVertexCount(coords))
    }

    // --- Combined evaluate() ---

    @Test
    fun evaluate_returns_empty_list_for_clean_plot() {
        val coords = (0 until 15).map { i ->
            val angle = 2 * Math.PI * i / 15
            GeoCoordinate(
                lat = 7.0 + 0.0005 * kotlin.math.cos(angle),
                lng = 38.0 + 0.0005 * kotlin.math.sin(angle),
                acc = 5.0
            )
        } + GeoCoordinate(
            lat = 7.0 + 0.0005 * kotlin.math.cos(0.0),
            lng = 38.0 + 0.0005 * kotlin.math.sin(0.0),
            acc = 5.0
        )

        val areaHectares = WarningRuleEngine.calculateAreaHectares(coords)
        val warnings = WarningRuleEngine.evaluate(coords, areaHectares)
        assertTrue("Expected no warnings for clean plot, got: " + warnings, warnings.isEmpty())
    }

    @Test
    fun evaluate_returns_multiple_warnings_when_multiple_rules_trigger() {
        val coords = listOf(
            GeoCoordinate(7.0, 38.0, 0.0, 20.0),
            GeoCoordinate(7.0, 38.01, 0.0, 18.0),
            GeoCoordinate(7.01, 38.01, 0.0, 22.0),
            GeoCoordinate(7.01, 38.0, 0.0, 19.0),
            GeoCoordinate(7.005, 38.005, 0.0, 20.0),
            GeoCoordinate(7.003, 38.002, 0.0, 21.0),
            GeoCoordinate(7.0, 38.0, 0.0, 20.0)
        )
        val areaHectares = 25.0
        val warnings = WarningRuleEngine.evaluate(coords, areaHectares)

        assertTrue("Expected multiple warnings", warnings.size >= 2)
        assertTrue(warnings.any { it.type == WarningType.GPS_ACCURACY_LOW })
        assertTrue(warnings.any { it.type == WarningType.AREA_TOO_LARGE })
    }

    // --- Calculate area ---

    @Test
    fun calculateAreaHectares_returns_zero_for_fewer_than_4_coordinates() {
        val coords = listOf(
            GeoCoordinate(7.0, 38.0),
            GeoCoordinate(7.001, 38.0),
            GeoCoordinate(7.0, 38.0)
        )
        assertEquals(0.0, WarningRuleEngine.calculateAreaHectares(coords), 0.001)
    }

    @Test
    fun calculateAreaHectares_reasonable_result_for_known_polygon() {
        val coords = listOf(
            GeoCoordinate(0.0, 0.0),
            GeoCoordinate(0.0009, 0.0),
            GeoCoordinate(0.0009, 0.0009),
            GeoCoordinate(0.0, 0.0009),
            GeoCoordinate(0.0, 0.0)
        )
        val area = WarningRuleEngine.calculateAreaHectares(coords)
        assertEquals(1.0, area, 0.2)
    }

    // --- Warning text format ---

    @Test
    fun warning_shortText_follows_compact_format() {
        val warning = WarningRuleEngine.checkAreaTooLarge(25.1)
        assertNotNull(warning)
        assertTrue(warning!!.shortText.startsWith("AREA_TOO_LARGE:"))
    }

    @Test
    fun warning_message_is_human_readable() {
        val warning = WarningRuleEngine.checkAreaTooLarge(25.1)
        assertNotNull(warning)
        assertTrue(warning!!.message.contains("ha"))
        assertTrue(warning.message.contains("threshold"))
    }
}
