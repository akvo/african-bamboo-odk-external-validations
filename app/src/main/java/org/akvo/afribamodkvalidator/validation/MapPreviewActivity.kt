package org.akvo.afribamodkvalidator.validation

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.akvo.afribamodkvalidator.data.dao.PlotDao
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKTReader
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import javax.inject.Inject
import org.osmdroid.views.overlay.Polygon as OsmPolygon

@AndroidEntryPoint
class MapPreviewActivity : AppCompatActivity() {

    @Inject
    lateinit var plotDao: PlotDao

    private lateinit var mapView: MapView
    private val wktReader = WKTReader()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure OSMDroid with extended cache for offline use
        val config = Configuration.getInstance()
        config.load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        config.userAgentValue = packageName  // Required by tile providers (e.g., OSM)
        config.osmdroidTileCache = filesDir  // Use app internal storage
        config.expirationOverrideDuration = CACHE_EXPIRATION_DAYS * 24 * 60 * 60 * 1000L

        // Create and configure map view
        mapView = MapView(this)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(18.0)

        setContentView(mapView)

        // Get intent extras
        val currentPolygonWkt = intent.getStringExtra(EXTRA_CURRENT_POLYGON_WKT)
        val currentPlotName = intent.getStringExtra(EXTRA_CURRENT_PLOT_NAME) ?: "Current Plot"
        val overlappingUuids = intent.getStringArrayListExtra(EXTRA_OVERLAPPING_UUIDS) ?: arrayListOf()

        if (currentPolygonWkt.isNullOrBlank()) {
            Toast.makeText(this, "No polygon data provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadAndDisplayPolygons(currentPolygonWkt, currentPlotName, overlappingUuids)
    }

    private fun loadAndDisplayPolygons(
        currentPolygonWkt: String,
        currentPlotName: String,
        overlappingUuids: List<String>
    ) {
        lifecycleScope.launch {
            try {
                val allPoints = mutableListOf<GeoPoint>()

                // Display current polygon (blue)
                val currentGeometry = parseWkt(currentPolygonWkt)
                if (currentGeometry != null) {
                    val currentOsmPolygon = createOsmPolygon(
                        geometry = currentGeometry,
                        fillColor = Color.argb(80, 0, 100, 255),
                        strokeColor = Color.rgb(0, 50, 200),
                        title = currentPlotName
                    )
                    mapView.overlays.add(currentOsmPolygon)
                    allPoints.addAll(currentOsmPolygon.actualPoints)
                }

                // Load and display overlapping polygons (red)
                if (overlappingUuids.isNotEmpty()) {
                    val overlappingPlots = plotDao.getPlotsByUuids(overlappingUuids)
                    for (plot in overlappingPlots) {
                        val geometry = parseWkt(plot.polygonWkt)
                        if (geometry != null) {
                            val osmPolygon = createOsmPolygon(
                                geometry = geometry,
                                fillColor = Color.argb(80, 255, 50, 50),
                                strokeColor = Color.rgb(200, 0, 0),
                                title = plot.plotName
                            )
                            mapView.overlays.add(osmPolygon)
                            allPoints.addAll(osmPolygon.actualPoints)
                        }
                    }
                }

                // Zoom to fit all polygons
                if (allPoints.isNotEmpty()) {
                    zoomToFitPoints(allPoints)
                }

                mapView.invalidate()

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

    private fun createOsmPolygon(
        geometry: Geometry,
        fillColor: Int,
        strokeColor: Int,
        title: String
    ): OsmPolygon {
        val coordinates = geometry.coordinates
        val geoPoints = coordinates.map { coord ->
            GeoPoint(coord.y, coord.x) // WKT uses x=lon, y=lat
        }

        return OsmPolygon(mapView).apply {
            points = geoPoints
            this.fillColor = fillColor
            this.strokeColor = strokeColor
            strokeWidth = 3f
            this.title = title
            setOnClickListener { polygon, _, _ ->
                Toast.makeText(
                    this@MapPreviewActivity,
                    polygon.title,
                    Toast.LENGTH_SHORT
                ).show()
                true
            }
        }
    }

    private fun zoomToFitPoints(points: List<GeoPoint>) {
        if (points.isEmpty()) return

        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }

        // Add padding
        val latPadding = (maxLat - minLat) * 0.2
        val lonPadding = (maxLon - minLon) * 0.2

        val boundingBox = BoundingBox(
            maxLat + latPadding,
            maxLon + lonPadding,
            minLat - latPadding,
            minLon - lonPadding
        )

        mapView.post {
            mapView.zoomToBoundingBox(boundingBox, true)
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDetach()
    }

    companion object {
        private const val CACHE_EXPIRATION_DAYS = 365L // 1 year cache for offline use
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
