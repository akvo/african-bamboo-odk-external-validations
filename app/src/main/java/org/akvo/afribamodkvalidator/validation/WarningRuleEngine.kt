package org.akvo.afribamodkvalidator.validation

import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Evaluates 5 warning rules against plot boundary data.
 *
 * Warning rules (W1–W5) flag plots for review without rejecting them.
 * All thresholds are hardcoded per African Bamboo agreement (January 2026).
 *
 * Rules are evaluated only on plots that have already passed reject-level
 * validation via [PolygonValidator].
 */
object WarningRuleEngine {

    /** W1: Average GPS accuracy threshold in meters */
    private const val GPS_ACCURACY_THRESHOLD = 15.0

    /** W2: Maximum gap between consecutive points in meters */
    private const val POINT_GAP_THRESHOLD = 50.0

    /** W3: Coefficient of Variation threshold for point spacing */
    private const val CV_THRESHOLD = 0.5

    /** W4: Maximum plot area in hectares */
    private const val AREA_THRESHOLD_HECTARES = 20.0

    /** W5: Vertex count range considered "too rough" (inclusive bounds) */
    private const val LOW_VERTEX_MIN = 6
    private const val LOW_VERTEX_MAX = 10

    private const val METERS_PER_DEGREE_AT_EQUATOR = 111_320.0

    /**
     * Evaluate all 5 warning rules against plot data.
     *
     * @param coordinates The parsed GeoCoordinates from the geoshape (including closing point)
     * @param areaHectares The plot area in hectares (from polygon area calculation)
     * @return List of warnings; empty if the plot is clean
     */
    fun evaluate(coordinates: List<GeoCoordinate>, areaHectares: Double): List<PlotWarning> {
        val warnings = mutableListOf<PlotWarning>()

        checkGpsAccuracy(coordinates)?.let { warnings.add(it) }
        warnings.addAll(checkPointGaps(coordinates))
        checkUnevenSpacing(coordinates)?.let { warnings.add(it) }
        checkAreaTooLarge(areaHectares)?.let { warnings.add(it) }
        checkLowVertexCount(coordinates)?.let { warnings.add(it) }

        return warnings
    }

    /**
     * Calculate area in hectares from polygon coordinates using the Shoelace formula
     * with latitude correction. Reuses the same approach as PolygonValidator.
     *
     * @param coordinates The parsed GeoCoordinates (must include closing point)
     * @return area in hectares
     */
    fun calculateAreaHectares(coordinates: List<GeoCoordinate>): Double {
        if (coordinates.size < 4) return 0.0 // Need at least 3 points + closing

        // Shoelace formula in degree-space
        var sum = 0.0
        for (i in 0 until coordinates.size - 1) {
            val c1 = coordinates[i]
            val c2 = coordinates[i + 1]
            sum += c1.lng * c2.lat - c2.lng * c1.lat
        }
        val areaInSquareDegrees = kotlin.math.abs(sum) / 2.0

        // Convert square degrees to square meters using centroid latitude
        val centroidLat = coordinates.map { it.lat }.average()
        val metersPerDegreeLat = METERS_PER_DEGREE_AT_EQUATOR
        val metersPerDegreeLng = METERS_PER_DEGREE_AT_EQUATOR * cos(Math.toRadians(centroidLat))
        val areaSquareMeters = areaInSquareDegrees * metersPerDegreeLat * metersPerDegreeLng

        return areaSquareMeters / 10_000.0 // Convert m² to hectares
    }

    /**
     * W1: Average GPS accuracy too low.
     * Skips points where accuracy is 0.0 (unavailable).
     * Returns null if all accuracy values are 0.0.
     */
    internal fun checkGpsAccuracy(coordinates: List<GeoCoordinate>): PlotWarning? {
        val validAccuracies = coordinates.map { it.acc }.filter { it > 0.0 }
        if (validAccuracies.isEmpty()) return null

        val avgAccuracy = validAccuracies.average()
        if (avgAccuracy <= GPS_ACCURACY_THRESHOLD) return null

        val rounded = String.format("%.1f", avgAccuracy)
        return PlotWarning(
            type = WarningType.GPS_ACCURACY_LOW,
            message = "Average GPS accuracy is ${rounded}m (threshold: ${GPS_ACCURACY_THRESHOLD.toInt()}m)",
            shortText = "GPS_ACCURACY_LOW: ${rounded}m (>${GPS_ACCURACY_THRESHOLD.toInt()}m)",
            value = avgAccuracy
        )
    }

    /**
     * W2: Gap between consecutive polygon points > 50m.
     * Returns one warning per segment that exceeds the threshold.
     */
    internal fun checkPointGaps(coordinates: List<GeoCoordinate>): List<PlotWarning> {
        if (coordinates.size < 2) return emptyList()

        val warnings = mutableListOf<PlotWarning>()
        for (i in 0 until coordinates.size - 1) {
            val distance = GeoMath.haversineDistance(coordinates[i], coordinates[i + 1])
            if (distance > POINT_GAP_THRESHOLD) {
                val rounded = String.format("%.1f", distance)
                warnings.add(
                    PlotWarning(
                        type = WarningType.POINT_GAP_LARGE,
                        message = "Gap of ${rounded}m between points ${i + 1}-${i + 2} (threshold: ${POINT_GAP_THRESHOLD.toInt()}m)",
                        shortText = "POINT_GAP_LARGE: ${rounded}m seg ${i + 1}-${i + 2} (>${POINT_GAP_THRESHOLD.toInt()}m)",
                        value = distance
                    )
                )
            }
        }
        return warnings
    }

    /**
     * W3: Uneven point spacing (CV > 0.5).
     * Needs at least 3 points (2 segments) for meaningful CV.
     */
    internal fun checkUnevenSpacing(coordinates: List<GeoCoordinate>): PlotWarning? {
        if (coordinates.size < 3) return null

        val distances = (0 until coordinates.size - 1).map { i ->
            GeoMath.haversineDistance(coordinates[i], coordinates[i + 1])
        }

        val cv = GeoMath.coefficientOfVariation(distances)
        if (cv <= CV_THRESHOLD) return null

        val rounded = String.format("%.2f", cv)
        return PlotWarning(
            type = WarningType.UNEVEN_SPACING,
            message = "Uneven point spacing (CV = $rounded, threshold: $CV_THRESHOLD)",
            shortText = "UNEVEN_SPACING: CV=$rounded (>$CV_THRESHOLD)",
            value = cv
        )
    }

    /**
     * W4: Plot area too large (> 20 ha).
     */
    internal fun checkAreaTooLarge(areaHectares: Double): PlotWarning? {
        if (areaHectares <= AREA_THRESHOLD_HECTARES) return null

        val rounded = String.format("%.1f", areaHectares)
        return PlotWarning(
            type = WarningType.AREA_TOO_LARGE,
            message = "Plot area is ${rounded}ha (threshold: ${AREA_THRESHOLD_HECTARES.toInt()}ha)",
            shortText = "AREA_TOO_LARGE: ${rounded}ha (>${AREA_THRESHOLD_HECTARES.toInt()}ha)",
            value = areaHectares
        )
    }

    /**
     * W5: Too few vertices (rough boundary).
     * Warning when vertex count (excluding closing point) is 6–10.
     * Does NOT flag < 6 (rejected by PolygonValidator) or > 10 (fine).
     */
    internal fun checkLowVertexCount(coordinates: List<GeoCoordinate>): PlotWarning? {
        // Exclude the closing point (last == first in a closed polygon)
        val vertexCount = if (coordinates.size >= 2 &&
            coordinates.first().lat == coordinates.last().lat &&
            coordinates.first().lng == coordinates.last().lng
        ) {
            coordinates.size - 1
        } else {
            coordinates.size
        }

        if (vertexCount < LOW_VERTEX_MIN || vertexCount > LOW_VERTEX_MAX) return null

        return PlotWarning(
            type = WarningType.LOW_VERTEX_COUNT,
            message = "Polygon has only $vertexCount vertices — boundary may be too rough",
            shortText = "LOW_VERTEX_COUNT: $vertexCount vertices ($LOW_VERTEX_MIN-$LOW_VERTEX_MAX)",
            value = vertexCount.toDouble()
        )
    }
}
