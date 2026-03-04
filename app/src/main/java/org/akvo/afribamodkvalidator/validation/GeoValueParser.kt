package org.akvo.afribamodkvalidator.validation

data class GeoCoordinate(
    val lat: Double,
    val lng: Double,
    val alt: Double = 0.0,
    val acc: Double = 0.0
)

sealed class GeoValue {
    abstract val coordinates: List<GeoCoordinate>

    data class GeoPoint(val coordinate: GeoCoordinate) : GeoValue() {
        override val coordinates: List<GeoCoordinate> = listOf(coordinate)
    }

    data class GeoTrace(override val coordinates: List<GeoCoordinate>) : GeoValue()

    data class GeoShape(override val coordinates: List<GeoCoordinate>) : GeoValue()
}

enum class GeoType {
    GEOPOINT, GEOTRACE, GEOSHAPE
}

object GeoValueParser {

    private val WHITESPACE_REGEX = "\\s+".toRegex()

    fun parse(value: String): GeoValue? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null

        val points = trimmed.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val coordinates = points.mapNotNull { parseCoordinate(it) }
        if (coordinates.isEmpty()) return null
        if (coordinates.size != points.size) return null

        return when {
            coordinates.size == 1 -> GeoValue.GeoPoint(coordinates.first())
            isClosed(coordinates) && coordinates.size >= 4 -> GeoValue.GeoShape(coordinates)
            coordinates.size >= 2 -> GeoValue.GeoTrace(coordinates)
            else -> null
        }
    }

    fun geoType(geoValue: GeoValue): GeoType = when (geoValue) {
        is GeoValue.GeoPoint -> GeoType.GEOPOINT
        is GeoValue.GeoTrace -> GeoType.GEOTRACE
        is GeoValue.GeoShape -> GeoType.GEOSHAPE
    }

    private fun parseCoordinate(pointStr: String): GeoCoordinate? {
        val parts = pointStr.split(WHITESPACE_REGEX)
        if (parts.size < 2) return null

        return try {
            val lat = parts[0].toDouble()
            val lng = parts[1].toDouble()

            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null

            val alt = parts.getOrNull(2)?.toDoubleOrNull() ?: 0.0
            val acc = parts.getOrNull(3)?.toDoubleOrNull() ?: 0.0

            GeoCoordinate(lat, lng, alt, acc)
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun isClosed(coordinates: List<GeoCoordinate>): Boolean {
        if (coordinates.size < 3) return false
        val first = coordinates.first()
        val last = coordinates.last()
        return first.lat == last.lat && first.lng == last.lng
    }
}
