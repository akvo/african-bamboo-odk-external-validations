package org.akvo.afribamodkvalidator.validation

import android.util.Log
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.io.WKTReader
import kotlin.math.cos

sealed class ValidationResult {
    data object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}

class PolygonValidator(
    private val minVertices: Int = MIN_VERTICES,
    private val minAreaSquareMeters: Double = MIN_AREA_SQ_METERS
) {
    private val geometryFactory = GeometryFactory()

    fun validate(input: String): ValidationResult {
        val polygon = parsePolygon(input)
            ?: return ValidationResult.Error("Invalid polygon format. Unable to parse the shape data.")

        // Check 1: Vertex count
        val coordinates = polygon.exteriorRing.coordinates
        if (coordinates.size < minVertices) {
            return ValidationResult.Error(
                "Error: Polygon has too few vertices. A valid shape requires at least 3 points."
            )
        }

        // Check 2: Area threshold
        val areaInSquareMeters = calculateAreaInSquareMeters(polygon)
        if (areaInSquareMeters < minAreaSquareMeters) {
            return ValidationResult.Error(
                "Error: Polygon area is too small. Minimum required: ${minAreaSquareMeters.toInt()} square meters."
            )
        }

        // Check 3: Self-intersection / topology
        if (!polygon.isValid) {
            return ValidationResult.Error(
                "Error: Polygon lines intersect or cross each other. Please redraw the shape."
            )
        }

        return ValidationResult.Success
    }

    private fun parsePolygon(input: String): Polygon? {
        return parseOdkGeoshape(input) ?: parseWkt(input)
    }

    /**
     * Parse input string to JTS Polygon.
     * Supports both ODK geoshape format and WKT.
     * Exposed for use by OverlapChecker when WKT parsing fails.
     */
    fun parseToJtsPolygon(input: String): Polygon? {
        return parsePolygon(input)
    }

    /**
     * Parse ODK geoshape format: "lat lng alt acc; lat lng alt acc; ..."
     * Each point is space-separated: latitude longitude altitude accuracy
     * Points are separated by semicolons
     */
    private fun parseOdkGeoshape(input: String): Polygon? {
        return try {
            val points = input.trim().split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { pointStr ->
                    val parts = pointStr.split(WHITESPACE_REGEX)
                    if (parts.size < 2) return null
                    val lat = parts[0].toDouble()
                    val lng = parts[1].toDouble()
                    // JTS uses (x, y) = (longitude, latitude)
                    Coordinate(lng, lat)
                }

            if (points.size < 3) return null

            // Ensure the polygon is closed
            val closedPoints = if (points.first() == points.last()) {
                points
            } else {
                points + points.first()
            }

            val shell = geometryFactory.createLinearRing(closedPoints.toTypedArray())
            geometryFactory.createPolygon(shell)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ODK geoshape format", e)
            null
        }
    }

    private fun parseWkt(input: String): Polygon? {
        return try {
            val reader = WKTReader(geometryFactory)
            reader.read(input) as? Polygon
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse WKT format", e)
            null
        }
    }

    /**
     * Convert polygon area from square degrees to square meters using a simple approximation.
     * Uses the centroid latitude for the conversion factor.
     */
    private fun calculateAreaInSquareMeters(polygon: Polygon): Double {
        val centroid = polygon.centroid
        val lat = centroid.y
        val areaInSquareDegrees = polygon.area

        // Convert square degrees to square meters
        // Longitude shrinks by cos(latitude)
        val metersPerDegreeLat = METERS_PER_DEGREE_AT_EQUATOR
        val metersPerDegreeLng = METERS_PER_DEGREE_AT_EQUATOR * cos(Math.toRadians(lat))

        return areaInSquareDegrees * metersPerDegreeLat * metersPerDegreeLng
    }

    companion object {
        private const val TAG = "PolygonValidator"
        private const val METERS_PER_DEGREE_AT_EQUATOR = 111320.0
        const val MIN_VERTICES = 4 // 3 distinct points + 1 closing point
        const val MIN_AREA_SQ_METERS = 10.0
        private val WHITESPACE_REGEX = "\\s+".toRegex()
    }
}
