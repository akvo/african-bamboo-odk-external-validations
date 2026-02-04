package org.akvo.afribamodkvalidator.validation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.RenderedQueryGeometry
import com.mapbox.maps.RenderedQueryOptions
import com.mapbox.maps.ViewAnnotationAnchor
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.fillLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.viewannotation.ViewAnnotationManager
import com.mapbox.maps.viewannotation.annotationAnchor
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.akvo.afribamodkvalidator.data.dao.PlotDao
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKTReader
import javax.inject.Inject

@AndroidEntryPoint
class MapPreviewActivity : AppCompatActivity() {

    @Inject
    lateinit var plotDao: PlotDao

    private lateinit var mapView: MapView
    private lateinit var viewAnnotationManager: ViewAnnotationManager
    private val wktReader = WKTReader()

    // Map source IDs to plot names for click handling
    private val sourceToPlotName = mutableMapOf<String, String>()
    private var currentPopupView: android.view.View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display
        enableEdgeToEdge()

        // Handle back press to close activity
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishAffinity()
            }
        })

        // Create container with proper insets handling
        val container = FrameLayout(this)
        mapView = MapView(this)
        container.addView(
            mapView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(container)

        // Apply window insets to avoid overlapping with system navigation bar
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

        // Get intent extras
        val currentPolygonWkt = intent.getStringExtra(EXTRA_CURRENT_POLYGON_WKT)
        val currentPlotName = intent.getStringExtra(EXTRA_CURRENT_PLOT_NAME) ?: "Current Plot"
        val overlappingUuids = intent.getStringArrayListExtra(EXTRA_OVERLAPPING_UUIDS) ?: arrayListOf()

        if (currentPolygonWkt.isNullOrBlank()) {
            Toast.makeText(this, "No polygon data provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize view annotation manager for popups
        viewAnnotationManager = mapView.viewAnnotationManager

        // Load satellite style and display polygons
        mapView.mapboxMap.loadStyle(Style.SATELLITE_STREETS) { style ->
            loadAndDisplayPolygons(style, currentPolygonWkt, currentPlotName, overlappingUuids)
            setupClickListener()
        }
    }

    private fun setupClickListener() {
        mapView.mapboxMap.addOnMapClickListener { point ->
            // Remove existing popup
            currentPopupView?.let { viewAnnotationManager.removeViewAnnotation(it) }
            currentPopupView = null

            // Query for features at the clicked point
            val screenPoint = mapView.mapboxMap.pixelForCoordinate(point)
            val queryGeometry = RenderedQueryGeometry(screenPoint)

            // Query all layers (no filter)
            val queryOptions = RenderedQueryOptions(null, null)

            mapView.mapboxMap.queryRenderedFeatures(queryGeometry, queryOptions) { result ->
                result.value?.let { features ->
                    if (features.isNotEmpty()) {
                        // Find the first feature with a registered source
                        for (feature in features) {
                            val sourceId = feature.queriedFeature.source
                            val plotName = sourceToPlotName[sourceId]
                            if (plotName != null) {
                                showPopup(point, plotName)
                                break
                            }
                        }
                    }
                }
            }
            true
        }
    }

    private fun showPopup(point: Point, plotName: String) {
        // Create rounded background drawable
        val backgroundDrawable = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.WHITE)
            cornerRadius = 24f
            setStroke(2, android.graphics.Color.parseColor("#CCCCCC"))
        }

        // Create popup view with proper LayoutParams
        val popupView = android.widget.TextView(this).apply {
            text = plotName
            background = backgroundDrawable
            setPadding(32, 20, 32, 24) // left, top, right, bottom
            setTextColor(android.graphics.Color.parseColor("#333333"))
            textSize = 15f
            maxWidth = 700
            elevation = 8f
            // Set LayoutParams - required by ViewAnnotationManager
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )

            // Dismiss popup when clicked
            setOnClickListener {
                viewAnnotationManager.removeViewAnnotation(this)
                currentPopupView = null
            }
        }

        // Add popup to map
        viewAnnotationManager.addViewAnnotation(
            popupView,
            viewAnnotationOptions {
                geometry(point)
                annotationAnchor {
                    anchor(ViewAnnotationAnchor.BOTTOM)
                    offsetY(8.0)
                }
            }
        )

        currentPopupView = popupView
    }

    private fun loadAndDisplayPolygons(
        style: Style,
        currentPolygonWkt: String,
        currentPlotName: String,
        overlappingUuids: List<String>
    ) {
        lifecycleScope.launch {
            try {
                val allPoints = mutableListOf<Point>()

                // Display current polygon (cyan - visible against green vegetation)
                val currentGeometry = parseWkt(currentPolygonWkt)
                if (currentGeometry != null) {
                    val points = addPolygonToMap(
                        style = style,
                        geometry = currentGeometry,
                        sourceId = "current-polygon-source",
                        fillLayerId = "current-polygon-fill",
                        lineLayerId = "current-polygon-line",
                        fillColor = "rgba(0, 255, 255, 0.35)",
                        strokeColor = "rgba(0, 255, 255, 1.0)",
                        title = currentPlotName
                    )
                    allPoints.addAll(points)
                }

                // Load and display overlapping polygons (red)
                if (overlappingUuids.isNotEmpty()) {
                    val overlappingPlots = plotDao.getPlotsByUuids(overlappingUuids)
                    overlappingPlots.forEachIndexed { index, plot ->
                        val geometry = parseWkt(plot.polygonWkt)
                        if (geometry != null) {
                            val points = addPolygonToMap(
                                style = style,
                                geometry = geometry,
                                sourceId = "overlap-polygon-source-$index",
                                fillLayerId = "overlap-polygon-fill-$index",
                                lineLayerId = "overlap-polygon-line-$index",
                                fillColor = "rgba(255, 50, 50, 0.3)",
                                strokeColor = "rgba(200, 0, 0, 1.0)",
                                title = plot.plotName
                            )
                            allPoints.addAll(points)
                        }
                    }
                }

                // Zoom to fit all polygons
                if (allPoints.isNotEmpty()) {
                    zoomToFitPoints(allPoints)
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@MapPreviewActivity,
                    "Error loading polygons: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun parseWkt(wkt: String): Geometry? {
        return try {
            wktReader.read(wkt)
        } catch (e: Exception) {
            null
        }
    }

    private fun addPolygonToMap(
        style: Style,
        geometry: Geometry,
        sourceId: String,
        fillLayerId: String,
        lineLayerId: String,
        fillColor: String,
        strokeColor: String,
        title: String
    ): List<Point> {
        // Convert JTS geometry to Mapbox Points
        val coordinates = geometry.coordinates
        val points = coordinates.map { coord ->
            Point.fromLngLat(coord.x, coord.y) // Mapbox uses lng, lat order
        }

        // Create GeoJSON polygon
        val polygon = Polygon.fromLngLats(listOf(points))

        // Add source
        style.addSource(
            geoJsonSource(sourceId) {
                geometry(polygon)
            }
        )

        // Add fill layer
        style.addLayer(
            fillLayer(fillLayerId, sourceId) {
                fillColor(fillColor)
                fillOpacity(0.5)
            }
        )

        // Add line layer for border
        style.addLayer(
            lineLayer(lineLayerId, sourceId) {
                lineColor(strokeColor)
                lineWidth(3.0)
            }
        )

        // Register source for click handling
        sourceToPlotName[sourceId] = title

        return points
    }

    private fun zoomToFitPoints(points: List<Point>) {
        if (points.isEmpty()) return

        val minLat = points.minOf { it.latitude() }
        val maxLat = points.maxOf { it.latitude() }
        val minLon = points.minOf { it.longitude() }
        val maxLon = points.maxOf { it.longitude() }

        // Calculate center
        val centerLat = (minLat + maxLat) / 2
        val centerLon = (minLon + maxLon) / 2

        // Calculate appropriate zoom level based on bounds
        val latDiff = maxLat - minLat
        val lonDiff = maxLon - minLon
        val maxDiff = maxOf(latDiff, lonDiff)

        // Approximate zoom level (higher zoom = more detailed)
        val zoom = when {
            maxDiff > 0.1 -> 12.0
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

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    companion object {
        const val EXTRA_CURRENT_POLYGON_WKT = "current_polygon_wkt"
        const val EXTRA_CURRENT_PLOT_NAME = "current_plot_name"
        const val EXTRA_OVERLAPPING_UUIDS = "overlapping_uuids"

        fun createIntent(
            context: Context,
            currentPolygonWkt: String,
            currentPlotName: String,
            overlappingUuids: List<String>
        ): Intent {
            return Intent(context, MapPreviewActivity::class.java).apply {
                putExtra(EXTRA_CURRENT_POLYGON_WKT, currentPolygonWkt)
                putExtra(EXTRA_CURRENT_PLOT_NAME, currentPlotName)
                putStringArrayListExtra(EXTRA_OVERLAPPING_UUIDS, ArrayList(overlappingUuids))
            }
        }
    }
}
