package org.akvo.afribamodkvalidator.validation

import android.content.Context
import com.mapbox.common.Cancelable
import com.mapbox.common.TileRegionError
import com.mapbox.common.TileRegionErrorType
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.common.TileStore
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.OfflineManager
import com.mapbox.maps.Style
import com.mapbox.maps.TilesetDescriptorOptions

/**
 * Manages offline tile downloads for Mapbox satellite maps.
 *
 * Uses Mapbox's TileStore to download satellite tiles for specified regions
 * for offline use in MapPreviewActivity.
 */
class MapboxOfflineManager(private val context: Context) {

    private val offlineManager = OfflineManager()
    private val tileStore = TileStore.create()
    private var currentDownload: Cancelable? = null

    /**
     * Download satellite tiles for a region.
     *
     * @param regionId Unique identifier for this download region
     * @param north Northern latitude bound
     * @param south Southern latitude bound
     * @param east Eastern longitude bound
     * @param west Western longitude bound
     * @param callback Progress and completion callbacks
     */
    fun downloadRegion(
        regionId: String,
        north: Double,
        south: Double,
        east: Double,
        west: Double,
        callback: DownloadCallback
    ) {
        callback.onStarted()

        // Create bounding box polygon
        val boundingBoxPolygon = Polygon.fromLngLats(
            listOf(
                listOf(
                    Point.fromLngLat(west, south), // SW
                    Point.fromLngLat(east, south), // SE
                    Point.fromLngLat(east, north), // NE
                    Point.fromLngLat(west, north), // NW
                    Point.fromLngLat(west, south)  // Close polygon
                )
            )
        )

        // Create tileset descriptor for satellite style
        val tilesetDescriptorOptions = TilesetDescriptorOptions.Builder()
            .styleURI(Style.SATELLITE_STREETS)
            .minZoom(ZOOM_MIN.toByte())
            .maxZoom(ZOOM_MAX.toByte())
            .pixelRatio(context.resources.displayMetrics.density)
            .build()

        val tilesetDescriptor = offlineManager.createTilesetDescriptor(tilesetDescriptorOptions)

        // Create load options
        val loadOptions = TileRegionLoadOptions.Builder()
            .geometry(boundingBoxPolygon)
            .descriptors(listOf(tilesetDescriptor))
            .acceptExpired(false)
            .build()

        // Start download
        currentDownload = tileStore.loadTileRegion(
            regionId,
            loadOptions,
            { progress ->
                // Progress callback
                val completedResources = progress.completedResourceCount
                val requiredResources = progress.requiredResourceCount
                callback.onProgress(completedResources.toInt(), requiredResources.toInt())
            }
        ) { expected ->
            expected.fold(
                { tileRegion ->
                    // Success
                    callback.onComplete()
                },
                { error ->
                    // Error or cancellation
                    if (error is TileRegionError && error.type == TileRegionErrorType.CANCELED) {
                        callback.onCanceled()
                    } else {
                        callback.onFailed(error.toString())
                    }
                }
            )
        }
    }

    /**
     * Cancel the current download.
     */
    fun cancelDownload() {
        currentDownload?.cancel()
        currentDownload = null
    }

    interface DownloadCallback {
        fun onStarted()
        fun onProgress(completed: Int, total: Int)
        fun onComplete()
        fun onFailed(error: String)
        fun onCanceled()
    }

    companion object {
        const val ZOOM_MIN = 15
        const val ZOOM_MAX = 18
    }
}
