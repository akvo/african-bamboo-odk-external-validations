package org.akvo.afribamodkvalidator.validation

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
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
import org.akvo.afribamodkvalidator.R
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
    private var currentPopupView: View? = null

    // Store polygon center for Google Maps button
    private var polygonCenterLat: Double = 0.0
    private var polygonCenterLon: Double = 0.0

    // Google Maps FAB and network monitoring
    private lateinit var googleMapsFab: FloatingActionButton
    private lateinit var connectivityManager: ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread { googleMapsFab.visibility = View.VISIBLE }
        }

        override fun onLost(network: Network) {
            runOnUiThread { googleMapsFab.visibility = View.GONE }
        }
    }

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

        // Add imagery disclaimer banner at bottom-left (avoids Mapbox scale legend at top)
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
        val bannerParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP
            marginStart = (6 * resources.displayMetrics.density).toInt()
            topMargin = (36 * resources.displayMetrics.density).toInt()
        }
        container.addView(disclaimerBanner, bannerParams)

        // Add Google Maps FAB button (conditionally visible based on network)
        googleMapsFab = FloatingActionButton(this).apply {
            setImageResource(android.R.drawable.ic_dialog_map)
            contentDescription = getString(R.string.view_in_google_maps)
            visibility = View.GONE // Hidden by default, shown when online
            setOnClickListener { openInGoogleMaps() }
        }
        val fabParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            marginEnd = (24 * resources.displayMetrics.density).toInt()
            bottomMargin = (24 * resources.displayMetrics.density).toInt()
        }
        container.addView(googleMapsFab, fabParams)

        // Setup network connectivity monitoring
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        // Check initial connectivity state
        val isOnline = connectivityManager.activeNetwork?.let { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } ?: false
        googleMapsFab.visibility = if (isOnline) View.VISIBLE else View.GONE

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
        val backgroundDrawable = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = 24f
            setStroke(2, "#CCCCCC".toColorInt())
        }

        // Create popup view with proper LayoutParams
        val popupView = TextView(this).apply {
            text = plotName
            background = backgroundDrawable
            setPadding(32, 20, 32, 24) // left, top, right, bottom
            setTextColor("#333333".toColorInt())
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
        } catch (_: Exception) {
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

    private fun openInGoogleMaps() {
        if (polygonCenterLat == 0.0 && polygonCenterLon == 0.0) {
            Toast.makeText(this, "Location not available", Toast.LENGTH_SHORT).show()
            return
        }

        // Open Google Maps in satellite view centered on the polygon
        // Using zoom level 18 for detailed satellite view
        val gmmIntentUri =
            "https://www.google.com/maps/@$polygonCenterLat,$polygonCenterLon,18z/data=!3m1!1e3".toUri()
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)

        if (mapIntent.resolveActivity(packageManager) != null) {
            startActivity(mapIntent)
        } else {
            // Fallback to browser if no app can handle it
            startActivity(Intent(Intent.ACTION_VIEW, gmmIntentUri))
        }
    }

    private fun zoomToFitPoints(points: List<Point>) {
        if (points.isEmpty()) return

        val minLat = points.minOf { it.latitude() }
        val maxLat = points.maxOf { it.latitude() }
        val minLon = points.minOf { it.longitude() }
        val maxLon = points.maxOf { it.longitude() }

        // Calculate center and store for Google Maps button
        val centerLat = (minLat + maxLat) / 2
        val centerLon = (minLon + maxLon) / 2
        polygonCenterLat = centerLat
        polygonCenterLon = centerLon

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

    override fun onDestroy() {
        super.onDestroy()
        connectivityManager.unregisterNetworkCallback(networkCallback)
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
