package org.akvo.afribamodkvalidator.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {

    @Test
    fun haversine_distance_between_same_point_is_zero() {
        val point = GeoCoordinate(lat = 7.0, lng = 38.0)
        assertEquals(0.0, GeoMath.haversineDistance(point, point), 0.01)
    }

    @Test
    fun haversine_distance_known_value_approximately_1_degree_latitude() {
        val a = GeoCoordinate(lat = 0.0, lng = 0.0)
        val b = GeoCoordinate(lat = 1.0, lng = 0.0)
        val distance = GeoMath.haversineDistance(a, b)
        assertEquals(111195.0, distance, 500.0)
    }

    @Test
    fun haversine_distance_Ethiopia_region_real_world_coordinates() {
        val a = GeoCoordinate(lat = 7.0, lng = 38.0)
        val b = GeoCoordinate(lat = 7.0009, lng = 38.0)
        val distance = GeoMath.haversineDistance(a, b)
        assertEquals(100.0, distance, 5.0)
    }

    @Test
    fun haversine_distance_short_segment_under_50m() {
        val a = GeoCoordinate(lat = 7.0, lng = 38.0)
        val b = GeoCoordinate(lat = 7.00027, lng = 38.0)
        val distance = GeoMath.haversineDistance(a, b)
        assertEquals(30.0, distance, 2.0)
    }

    @Test
    fun haversine_distance_long_segment_over_50m() {
        val a = GeoCoordinate(lat = 7.0, lng = 38.0)
        val b = GeoCoordinate(lat = 7.000675, lng = 38.0)
        val distance = GeoMath.haversineDistance(a, b)
        assertEquals(75.0, distance, 3.0)
    }

    @Test
    fun haversine_distance_diagonal_movement() {
        val a = GeoCoordinate(lat = 7.0, lng = 38.0)
        val b = GeoCoordinate(lat = 7.0009, lng = 38.0009)
        val distance = GeoMath.haversineDistance(a, b)
        assertTrue("Distance should be > 100m for diagonal", distance > 100.0)
        assertTrue("Distance should be < 200m for diagonal", distance < 200.0)
    }

    @Test
    fun cv_of_empty_list_returns_zero() {
        assertEquals(0.0, GeoMath.coefficientOfVariation(emptyList()), 0.001)
    }

    @Test
    fun cv_of_single_value_returns_zero() {
        assertEquals(0.0, GeoMath.coefficientOfVariation(listOf(42.0)), 0.001)
    }

    @Test
    fun cv_of_identical_values_returns_zero() {
        assertEquals(0.0, GeoMath.coefficientOfVariation(listOf(10.0, 10.0, 10.0, 10.0)), 0.001)
    }

    @Test
    fun cv_of_all_zeros_returns_zero() {
        assertEquals(0.0, GeoMath.coefficientOfVariation(listOf(0.0, 0.0, 0.0)), 0.001)
    }

    @Test
    fun cv_of_evenly_spaced_values_is_low() {
        val cv = GeoMath.coefficientOfVariation(listOf(10.0, 11.0, 10.0, 11.0))
        assertTrue("CV should be < 0.5 for even spacing, was " + cv, cv < 0.5)
    }

    @Test
    fun cv_of_highly_variable_values_is_high() {
        val cv = GeoMath.coefficientOfVariation(listOf(5.0, 50.0, 5.0, 50.0))
        assertTrue("CV should be > 0.5 for uneven spacing, was " + cv, cv > 0.5)
    }

    @Test
    fun cv_known_calculation() {
        val cv = GeoMath.coefficientOfVariation(listOf(10.0, 20.0, 30.0))
        assertEquals(0.408, cv, 0.01)
    }
}
