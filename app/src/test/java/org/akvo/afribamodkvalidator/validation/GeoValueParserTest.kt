package org.akvo.afribamodkvalidator.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoValueParserTest {

    @Test
    fun `parse valid geopoint`() {
        val result = GeoValueParser.parse("1.234 36.789 0 0")
        assertNotNull(result)
        assertTrue(result is GeoValue.GeoPoint)
        val point = result as GeoValue.GeoPoint
        assertEquals(1.234, point.coordinate.lat, 0.001)
        assertEquals(36.789, point.coordinate.lng, 0.001)
    }

    @Test
    fun `parse geopoint with only lat lng`() {
        val result = GeoValueParser.parse("1.234 36.789")
        assertNotNull(result)
        assertTrue(result is GeoValue.GeoPoint)
    }

    @Test
    fun `parse valid geotrace`() {
        val result = GeoValueParser.parse("1.0 36.0 0 0; 2.0 37.0 0 0; 3.0 38.0 0 0")
        assertNotNull(result)
        assertTrue(result is GeoValue.GeoTrace)
        val trace = result as GeoValue.GeoTrace
        assertEquals(3, trace.coordinates.size)
    }

    @Test
    fun `parse valid geoshape - closed polygon`() {
        val result = GeoValueParser.parse(
            "1.0 36.0 0 0; 2.0 37.0 0 0; 3.0 38.0 0 0; 1.0 36.0 0 0"
        )
        assertNotNull(result)
        assertTrue(result is GeoValue.GeoShape)
        val shape = result as GeoValue.GeoShape
        assertEquals(4, shape.coordinates.size)
    }

    @Test
    fun `parse geoshape with many points`() {
        val result = GeoValueParser.parse(
            "0.0 36.0 0 0; 1.0 37.0 0 0; 2.0 37.0 0 0; 1.0 36.0 0 0; 0.0 36.0 0 0"
        )
        assertNotNull(result)
        assertTrue(result is GeoValue.GeoShape)
    }

    @Test
    fun `returns null for empty string`() {
        assertNull(GeoValueParser.parse(""))
    }

    @Test
    fun `returns null for blank string`() {
        assertNull(GeoValueParser.parse("   "))
    }

    @Test
    fun `returns null for non-geo text`() {
        assertNull(GeoValueParser.parse("hello world"))
    }

    @Test
    fun `returns null for single number`() {
        assertNull(GeoValueParser.parse("42"))
    }

    @Test
    fun `returns null for out of range latitude`() {
        assertNull(GeoValueParser.parse("91.0 36.0 0 0"))
    }

    @Test
    fun `returns null for out of range longitude`() {
        assertNull(GeoValueParser.parse("1.0 181.0 0 0"))
    }

    @Test
    fun `returns null for negative out of range`() {
        assertNull(GeoValueParser.parse("-91.0 36.0 0 0"))
        assertNull(GeoValueParser.parse("1.0 -181.0 0 0"))
    }

    @Test
    fun `boundary values are valid`() {
        assertNotNull(GeoValueParser.parse("90.0 180.0 0 0"))
        assertNotNull(GeoValueParser.parse("-90.0 -180.0 0 0"))
        assertNotNull(GeoValueParser.parse("0.0 0.0 0 0"))
    }

    @Test
    fun `three point closed polygon is a geoshape`() {
        // 3 unique points + closing = 4 total, minimum for geoshape
        val result = GeoValueParser.parse("1.0 36.0 0 0; 2.0 37.0 0 0; 3.0 38.0 0 0; 1.0 36.0 0 0")
        assertTrue(result is GeoValue.GeoShape)
    }

    @Test
    fun `three point closed polygon with only 3 points is a geotrace`() {
        // 2 unique points + closing = 3 total, not enough for geoshape
        val result = GeoValueParser.parse("1.0 36.0 0 0; 2.0 37.0 0 0; 1.0 36.0 0 0")
        assertTrue(result is GeoValue.GeoTrace)
    }

    @Test
    fun `geoType returns correct type`() {
        val point = GeoValueParser.parse("1.0 36.0 0 0")!!
        assertEquals(GeoType.GEOPOINT, GeoValueParser.geoType(point))

        val trace = GeoValueParser.parse("1.0 36.0 0 0; 2.0 37.0 0 0")!!
        assertEquals(GeoType.GEOTRACE, GeoValueParser.geoType(trace))

        val shape = GeoValueParser.parse("1.0 36.0 0 0; 2.0 37.0 0 0; 3.0 38.0 0 0; 1.0 36.0 0 0")!!
        assertEquals(GeoType.GEOSHAPE, GeoValueParser.geoType(shape))
    }

    @Test
    fun `partially invalid multi-point returns null`() {
        // One valid point, one invalid — should fail entirely
        assertNull(GeoValueParser.parse("1.0 36.0 0 0; hello world"))
    }

    @Test
    fun `handles extra whitespace`() {
        val result = GeoValueParser.parse("  1.0  36.0  0  0  ")
        assertNotNull(result)
        assertTrue(result is GeoValue.GeoPoint)
    }

    @Test
    fun `returns null for non-numeric altitude`() {
        assertNull(GeoValueParser.parse("1.0 36.0 foo 0"))
    }

    @Test
    fun `returns null for non-numeric accuracy`() {
        assertNull(GeoValueParser.parse("1.0 36.0 0 bar"))
    }
}
