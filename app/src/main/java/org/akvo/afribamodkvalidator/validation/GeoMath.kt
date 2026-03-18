package org.akvo.afribamodkvalidator.validation

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geographic and statistical math utilities for warning rule calculations.
 */
object GeoMath {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /**
     * Calculates the Haversine distance between two geographic coordinates.
     *
     * @return distance in meters
     */
    fun haversineDistance(a: GeoCoordinate, b: GeoCoordinate): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)

        val h = sin(dLat / 2).pow(2) +
                cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)

        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h))
    }

    /**
     * Calculates the Coefficient of Variation (CV) for a list of values.
     * CV = standard deviation / mean
     *
     * @return CV value, or 0.0 if the list has fewer than 2 values or mean is 0
     */
    fun coefficientOfVariation(values: List<Double>): Double {
        if (values.size < 2) return 0.0

        val mean = values.average()
        if (mean == 0.0) return 0.0

        val variance = values.sumOf { (it - mean).pow(2) } / values.size
        val stdDev = sqrt(variance)

        return stdDev / mean
    }
}
