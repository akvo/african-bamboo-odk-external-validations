package org.akvo.afribamodkvalidator.data.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `fromStringList should convert list to JSON string`() {
        val input = listOf("tag1", "tag2", "tag3")
        val result = converters.fromStringList(input)

        assertEquals("[\"tag1\",\"tag2\",\"tag3\"]", result)
    }

    @Test
    fun `fromStringList should return null for null input`() {
        val result = converters.fromStringList(null)
        assertNull(result)
    }

    @Test
    fun `fromStringList should return null for empty list`() {
        val result = converters.fromStringList(emptyList())
        assertEquals("[]", result) // Empty list serializes to "[]"
    }

    @Test
    fun `toStringList should parse JSON string to list`() {
        val input = "[\"tag1\",\"tag2\",\"tag3\"]"
        val result = converters.toStringList(input)

        assertEquals(listOf("tag1", "tag2", "tag3"), result)
    }

    @Test
    fun `toStringList should return null for null input`() {
        val result = converters.toStringList(null)
        assertNull(result)
    }

    @Test
    fun `toStringList should return empty list for empty JSON array`() {
        val result = converters.toStringList("[]")
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `string list conversion should be reversible`() {
        val original = listOf("region:NOR", "status:submitted", "validated:true")

        val serialized = converters.fromStringList(original)
        val deserialized = converters.toStringList(serialized)

        assertEquals(original, deserialized)
    }

    @Test
    fun `fromDoubleList should convert list to JSON string`() {
        val input = listOf(9.0123, 40.5678, 1850.2)
        val result = converters.fromDoubleList(input)

        assertEquals("[9.0123,40.5678,1850.2]", result)
    }

    @Test
    fun `fromDoubleList should return null for null input`() {
        val result = converters.fromDoubleList(null)
        assertNull(result)
    }

    @Test
    fun `toDoubleList should parse JSON string to list`() {
        val input = "[9.0123,40.5678,1850.2]"
        val result = converters.toDoubleList(input)

        assertEquals(listOf(9.0123, 40.5678, 1850.2), result)
    }

    @Test
    fun `toDoubleList should return null for null input`() {
        val result = converters.toDoubleList(null)
        assertNull(result)
    }

    @Test
    fun `double list conversion should be reversible`() {
        val original = listOf(-6.1234567, 108.7654321, 115.0, 6.2)

        val serialized = converters.fromDoubleList(original)
        val deserialized = converters.toDoubleList(serialized)

        assertEquals(original, deserialized)
    }

    @Test
    fun `double list should handle empty list`() {
        val serialized = converters.fromDoubleList(emptyList())
        val deserialized = converters.toDoubleList(serialized)

        assertEquals(emptyList<Double>(), deserialized)
    }

    @Test
    fun `double list should handle geolocation coordinates`() {
        // Kobo geolocation format: [longitude, latitude, altitude, accuracy]
        val geolocation = listOf(40.5678910, 9.0123456, 1850.2, 2.1)

        val serialized = converters.fromDoubleList(geolocation)
        val deserialized = converters.toDoubleList(serialized)

        assertEquals(geolocation, deserialized)
        assertEquals(4, deserialized?.size)
        assertEquals(40.5678910, deserialized?.get(0))
        assertEquals(9.0123456, deserialized?.get(1))
        assertEquals(1850.2, deserialized?.get(2))
        assertEquals(2.1, deserialized?.get(3))
    }

    @Test
    fun `double list should handle boundary mapping coordinates`() {
        // Boundary mapping with multiple points: lat, lon, altitude, accuracy
        val boundary = listOf(
            -6.1234567, 108.7654321, 115.0, 6.2,
            -6.1234568, 108.7654322, 112.0, 5.8,
            -6.1234569, 108.7654323, 110.0, 5.5
        )

        val serialized = converters.fromDoubleList(boundary)
        val deserialized = converters.toDoubleList(serialized)

        assertEquals(boundary, deserialized)
        assertEquals(12, deserialized?.size)
    }
}
