package org.akvo.afribamodkvalidator.validation

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.gestures.gestures
import org.akvo.afribamodkvalidator.R

/**
 * Lightweight Activity to preview downloaded satellite tiles for a region.
 *
 * Displays a Mapbox MapView centered on the region's bounding box using
 * satellite imagery. Downloaded tiles are automatically used by the SDK.
 */
class TilePreviewActivity : AppCompatActivity() {

    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val regionName = intent.getStringExtra(EXTRA_REGION_NAME) ?: "Region"
        val north = intent.getDoubleExtra(EXTRA_NORTH, 0.0)
        val south = intent.getDoubleExtra(EXTRA_SOUTH, 0.0)
        val east = intent.getDoubleExtra(EXTRA_EAST, 0.0)
        val west = intent.getDoubleExtra(EXTRA_WEST, 0.0)

        val container = FrameLayout(this)

        mapView = MapView(this)
        container.addView(
            mapView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // Region name banner at the top
        val nameBanner = TextView(this).apply {
            text = regionName
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(
                (14 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt(),
                (14 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt()
            )
            setBackgroundColor("#CC000000".toColorInt())
        }
        val nameBannerParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = (36 * resources.displayMetrics.density).toInt()
        }
        container.addView(nameBanner, nameBannerParams)

        // Imagery disclaimer banner at bottom
        val disclaimerBanner = TextView(this).apply {
            text = getString(R.string.imagery_disclaimer)
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(
                (10 * resources.displayMetrics.density).toInt(),
                (6 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt(),
                (6 * resources.displayMetrics.density).toInt()
            )
            setBackgroundColor("#99000000".toColorInt())
        }
        val disclaimerParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            marginStart = (6 * resources.displayMetrics.density).toInt()
            bottomMargin = (6 * resources.displayMetrics.density).toInt()
        }
        container.addView(disclaimerBanner, disclaimerParams)

        setContentView(container)

        ViewCompat.setOnApplyWindowInsetsListener(container) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = insets.left,
                top = insets.top,
                right = insets.right,
                bottom = insets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }

        // Enable gestures
        mapView.gestures.apply {
            pinchToZoomEnabled = true
            scrollEnabled = true
            rotateEnabled = true
            doubleTapToZoomInEnabled = true
        }

        // Load satellite style and center on region
        mapView.mapboxMap.loadStyle(Style.SATELLITE_STREETS) {
            val centerLat = (north + south) / 2
            val centerLon = (east + west) / 2
            val latDiff = north - south
            val lonDiff = east - west
            val maxDiff = maxOf(latDiff, lonDiff)

            val zoom = when {
                maxDiff > 1.0 -> 8.0
                maxDiff > 0.5 -> 9.0
                maxDiff > 0.1 -> 11.0
                maxDiff > 0.01 -> 14.0
                maxDiff > 0.001 -> 16.0
                else -> 18.0
            }

            mapView.mapboxMap.setCamera(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(centerLon, centerLat))
                    .zoom(zoom)
                    .build()
            )
        }
    }

    companion object {
        private const val EXTRA_REGION_NAME = "region_name"
        private const val EXTRA_NORTH = "north"
        private const val EXTRA_SOUTH = "south"
        private const val EXTRA_EAST = "east"
        private const val EXTRA_WEST = "west"

        fun createIntent(
            context: Context,
            regionName: String,
            north: Double,
            south: Double,
            east: Double,
            west: Double
        ): Intent {
            return Intent(context, TilePreviewActivity::class.java).apply {
                putExtra(EXTRA_REGION_NAME, regionName)
                putExtra(EXTRA_NORTH, north)
                putExtra(EXTRA_SOUTH, south)
                putExtra(EXTRA_EAST, east)
                putExtra(EXTRA_WEST, west)
            }
        }
    }
}
