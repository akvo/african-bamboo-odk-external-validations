package org.akvo.afribamodkvalidator.validation

import android.util.Log
import org.akvo.afribamodkvalidator.data.entity.PlotEntity
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.io.WKTReader
import kotlin.math.min

/**
 * Bounding box for spatial queries.
 */
data class BoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
)

/**
 * Result of an overlap check between two polygons.
 */
data class OverlapResult(
    val uuid: String,
    val plotName: String,
    val overlappingPolygon: Polygon,
    val overlapPercentage: Double
)

/**
 * Checks for overlaps between polygons using JTS geometry operations.
 *
 * Overlap threshold: Only overlaps >= 5% of the smaller polygon's area are reported.
 * This allows minor boundary touching without blocking validation.
 */
class OverlapChecker(
    private val overlapThresholdPercent: Double = DEFAULT_OVERLAP_THRESHOLD_PERCENT
) {
    private val geometryFactory = GeometryFactory()
    private val wktReader = WKTReader(geometryFactory)

    /**
     * Compute the bounding box of a polygon for SQL pre-filtering.
     */
    fun computeBoundingBox(polygon: Polygon): BoundingBox {
        val envelope = polygon.envelopeInternal
        return BoundingBox(
            minLat = envelope.minY,
            maxLat = envelope.maxY,
            minLon = envelope.minX,
            maxLon = envelope.maxX
        )
    }

    /**
     * Check for significant overlaps between a new polygon and candidate plots.
     *
     * @param newPolygon The polygon to check for overlaps
     * @param candidates List of existing plots from the database (pre-filtered by bbox)
     * @return List of overlap results where overlap percentage >= threshold
     */
    fun checkOverlaps(
        newPolygon: Polygon,
        candidates: List<PlotEntity>
    ): List<OverlapResult> {
        val results = mutableListOf<OverlapResult>()
        val newPolygonArea = newPolygon.area

        for (candidate in candidates) {
            try {
                val candidatePolygon = parseWkt(candidate.polygonWkt) ?: continue

                if (!newPolygon.intersects(candidatePolygon)) {
                    continue
                }

                val intersection = newPolygon.intersection(candidatePolygon)
                val intersectionArea = intersection.area

                if (intersectionArea <= 0) {
                    continue
                }

                val candidateArea = candidatePolygon.area
                val smallerArea = min(newPolygonArea, candidateArea)
                val overlapPercentage = (intersectionArea / smallerArea) * 100

                if (overlapPercentage >= overlapThresholdPercent) {
                    results.add(
                        OverlapResult(
                            uuid = candidate.uuid,
                            plotName = candidate.plotName,
                            overlappingPolygon = candidatePolygon,
                            overlapPercentage = overlapPercentage
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check overlap with plot ${candidate.uuid}", e)
            }
        }

        return results
    }

    /**
     * Parse WKT string to JTS Polygon.
     */
    fun parseWkt(wkt: String): Polygon? {
        return try {
            val geometry = wktReader.read(wkt)
            when (geometry) {
                is Polygon -> geometry
                else -> {
                    Log.w(TAG, "WKT is not a polygon: ${geometry.geometryType}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse WKT", e)
            null
        }
    }

    /**
     * Convert a Polygon to WKT string for storage.
     */
    fun toWkt(polygon: Polygon): String {
        return polygon.toText()
    }

    companion object {
        private const val TAG = "OverlapChecker"
        const val DEFAULT_OVERLAP_THRESHOLD_PERCENT = 5.0
    }
}
