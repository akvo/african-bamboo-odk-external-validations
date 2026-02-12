package org.akvo.afribamodkvalidator.data.model

import kotlinx.serialization.Serializable

@Serializable
data class OfflineRegionsConfig(
    val regions: List<OfflineRegionJson>
)

@Serializable
data class OfflineRegionJson(
    val name: String,
    val north: Double,
    val east: Double,
    val south: Double,
    val west: Double
)

/**
 * Geographic bounding box for offline map regions.
 */
data class MapBoundingBox(
    val latNorth: Double,
    val lonEast: Double,
    val latSouth: Double,
    val lonWest: Double
)

/**
 * Domain model for offline region with calculated properties.
 */
data class OfflineRegion(
    val name: String,
    val boundingBox: MapBoundingBox,
    val estimatedTiles: Int = 0,
    val estimatedSizeMb: Int = 0
)

fun OfflineRegionJson.toDomain(): OfflineRegion {
    return OfflineRegion(
        name = name,
        boundingBox = MapBoundingBox(
            latNorth = north,
            lonEast = east,
            latSouth = south,
            lonWest = west
        )
    )
}
