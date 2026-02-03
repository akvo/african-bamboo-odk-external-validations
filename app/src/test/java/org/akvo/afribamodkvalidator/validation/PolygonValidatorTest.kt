package org.akvo.afribamodkvalidator.validation

import org.junit.Assert.assertTrue
import org.junit.Test

class PolygonValidatorTest {

    private val validator = PolygonValidator()

    // Test D: Valid polygon - happy path
    @Test
    fun `valid large polygon passes all checks`() {
        // A large square in ODK format (approx 100m x 100m at equator)
        // lat lng alt acc format
        val validPolygon = """
            0.0 0.0 0 0;
            0.001 0.0 0 0;
            0.001 0.001 0 0;
            0.0 0.001 0 0;
            0.0 0.0 0 0
        """.trimIndent()

        val result = validator.validate(validPolygon)
        assertTrue("Expected Success but got $result", result is ValidationResult.Success)
    }

    // Test C: Too few vertices
    @Test
    fun `polygon with too few vertices fails`() {
        // Only 2 points (not enough to form a polygon)
        val twoPoints = "0.0 0.0 0 0; 1.0 1.0 0 0"

        val result = validator.validate(twoPoints)
        assertTrue("Expected Error but got $result", result is ValidationResult.Error)
        // Could be "Invalid polygon format" or "too few vertices" depending on parsing
        val errorMessage = (result as ValidationResult.Error).message
        assertTrue(
            "Expected error about vertices or format but got: $errorMessage",
            errorMessage.contains("too few vertices") || errorMessage.contains("Invalid polygon format")
        )
    }

    @Test
    fun `triangle with 3 points plus closing passes minimum vertex check`() {
        // 3 points auto-closed to 4 points meets MIN_VERTICES = 4
        // Large triangle (10 degrees ≈ 1,113 km) passes area check
        val triangle = "0.0 0.0 0 0; 10.0 10.0 0 0; 0.0 10.0 0 0"

        val result = validator.validate(triangle)

        assertTrue("Expected Success but got $result", result is ValidationResult.Success)
    }

    // Test A: Polygon area too small
    @Test
    fun `polygon with area too small fails`() {
        // A tiny polygon (1 meter squared area)
        val tinyPolygon = """
            0.0 0.0 0 0;
            0.000001 0.0 0 0;
            0.000001 0.000001 0 0;
            0.0 0.000001 0 0;
            0.0 0.0 0 0
        """.trimIndent()

        val result = validator.validate(tinyPolygon)
        assertTrue("Expected Error but got $result", result is ValidationResult.Error)
        assertTrue((result as ValidationResult.Error).message.contains("too small"))
    }

    // Test B: Self-intersecting (bowtie) polygon
    @Test
    fun `self-intersecting bowtie polygon fails`() {
        // A bowtie/figure-8 shape in WKT format
        // Creates a polygon where edges cross: (0,0) -> (1,1) -> (1,0) -> (0,1) -> (0,0)
        // This is a classic self-intersection pattern
        val bowtieWkt = "POLYGON ((0 0, 100 100, 100 0, 0 100, 0 0))"

        // Use a validator with very low area threshold to ensure we test intersection
        val testValidator = PolygonValidator(minAreaSquareMeters = 0.0)
        val result = testValidator.validate(bowtieWkt)

        // Should fail on self-intersection check
        assertTrue("Expected Error for self-intersecting polygon but got $result", result is ValidationResult.Error)
        val errorMessage = (result as ValidationResult.Error).message
        assertTrue(
            "Expected error about intersection or crossing but got: $errorMessage",
            errorMessage.contains("intersect") || errorMessage.contains("cross")
        )
    }

    // WKT format parsing
    @Test
    fun `valid WKT polygon passes validation`() {
        // A large polygon in WKT format
        val wktPolygon = "POLYGON ((0 0, 0.001 0, 0.001 0.001, 0 0.001, 0 0))"

        val result = validator.validate(wktPolygon)
        assertTrue("Expected Success but got $result", result is ValidationResult.Success)
    }

    @Test
    fun `invalid WKT format fails`() {
        val invalidWkt = "NOT A POLYGON"

        val result = validator.validate(invalidWkt)
        assertTrue(result is ValidationResult.Error)
        assertTrue((result as ValidationResult.Error).message.contains("Invalid polygon format"))
    }

    // Empty/null input handling
    @Test
    fun `empty string fails validation`() {
        val result = validator.validate("")

        assertTrue(result is ValidationResult.Error)
    }

    @Test
    fun `whitespace only string fails validation`() {
        val result = validator.validate("   ")

        assertTrue(result is ValidationResult.Error)
    }

    // ODK format parsing edge cases
    @Test
    fun `ODK format without altitude and accuracy parses correctly`() {
        // Some ODK implementations may only pass lat/lng
        val simpleOdk = "0.0 0.0; 0.001 0.0; 0.001 0.001; 0.0 0.001; 0.0 0.0"

        val result = validator.validate(simpleOdk)
        assertTrue("Expected Success but got $result", result is ValidationResult.Success)
    }

    // Custom threshold validation
    @Test
    fun `custom minimum area threshold works`() {
        // 0.001 degrees is about 111 meters, so 0.001 x 0.001 is about 12,321 sq meters
        // Use a very strict threshold that this will fail
        val strictValidator = PolygonValidator(minAreaSquareMeters = 50000.0)

        val mediumPolygon = """
            0.0 0.0 0 0;
            0.001 0.0 0 0;
            0.001 0.001 0 0;
            0.0 0.001 0 0;
            0.0 0.0 0 0
        """.trimIndent()

        val result = strictValidator.validate(mediumPolygon)
        assertTrue("Expected Error for small area but got $result", result is ValidationResult.Error)
        assertTrue((result as ValidationResult.Error).message.contains("too small"))
    }

    @Test
    fun `custom minimum vertices threshold works`() {
        val strictValidator = PolygonValidator(minVertices = 5)

        // A triangle (4 points including closing) that passes default but fails strict
        val triangle = "0.0 0.0 0 0; 0.001 0.0 0 0; 0.0005 0.001 0 0; 0.0 0.0 0 0"

        val result = strictValidator.validate(triangle)
        assertTrue(result is ValidationResult.Error)
        assertTrue((result as ValidationResult.Error).message.contains("too few vertices"))
    }
}
