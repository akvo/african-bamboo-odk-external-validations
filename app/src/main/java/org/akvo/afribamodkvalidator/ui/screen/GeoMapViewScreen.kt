package org.akvo.afribamodkvalidator.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.CircleLayer
import com.mapbox.maps.extension.style.layers.generated.FillLayer
import com.mapbox.maps.extension.style.layers.generated.LineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import org.akvo.afribamodkvalidator.ui.viewmodel.GeoMapViewUiState
import org.akvo.afribamodkvalidator.ui.viewmodel.GeoMapViewViewModel
import org.akvo.afribamodkvalidator.validation.GeoCoordinate
import org.akvo.afribamodkvalidator.validation.GeoValue

@Composable
fun GeoMapViewScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GeoMapViewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    GeoMapViewContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeoMapViewContent(
    uiState: GeoMapViewUiState,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.title.ifEmpty { "Map View" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.error,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            uiState.geoValue != null -> {
                GeoMapView(
                    geoValue = uiState.geoValue,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun GeoMapView(
    geoValue: GeoValue,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    AndroidView(
        factory = {
            mapView.apply {
                mapboxMap.loadStyle(Style.SATELLITE_STREETS) { style ->
                    renderGeoValue(style, geoValue)
                    zoomToFit(mapboxMap, geoValue)
                }
            }
        },
        modifier = modifier
    )
}

private fun renderGeoValue(style: Style, geoValue: GeoValue) {
    when (geoValue) {
        is GeoValue.GeoPoint -> renderPoint(style, geoValue.coordinate)
        is GeoValue.GeoTrace -> renderTrace(style, geoValue.coordinates)
        is GeoValue.GeoShape -> renderShape(style, geoValue.coordinates)
    }
}

private fun renderPoint(style: Style, coordinate: GeoCoordinate) {
    val point = Point.fromLngLat(coordinate.lng, coordinate.lat)

    val source = GeoJsonSource.Builder("geo-point-source")
        .geometry(point)
        .build()
    style.addSource(source)

    val circleLayer = CircleLayer("geo-point-layer", "geo-point-source")
    circleLayer.circleRadius(8.0)
    circleLayer.circleColor(android.graphics.Color.parseColor("#00BCD4"))
    circleLayer.circleStrokeWidth(2.0)
    circleLayer.circleStrokeColor(android.graphics.Color.WHITE)
    style.addLayer(circleLayer)
}

private fun renderTrace(style: Style, coordinates: List<GeoCoordinate>) {
    val points = coordinates.map { Point.fromLngLat(it.lng, it.lat) }
    val lineString = LineString.fromLngLats(points)

    val source = GeoJsonSource.Builder("geo-trace-source")
        .geometry(lineString)
        .build()
    style.addSource(source)

    val lineLayer = LineLayer("geo-trace-line-layer", "geo-trace-source")
    lineLayer.lineColor(android.graphics.Color.parseColor("#00BCD4"))
    lineLayer.lineWidth(3.0)
    style.addLayer(lineLayer)
}

private fun renderShape(style: Style, coordinates: List<GeoCoordinate>) {
    val points = coordinates.map { Point.fromLngLat(it.lng, it.lat) }
    val closedPoints = if (points.first() != points.last()) {
        points + points.first()
    } else {
        points
    }
    val polygon = Polygon.fromLngLats(listOf(closedPoints))

    val source = GeoJsonSource.Builder("geo-shape-source")
        .geometry(polygon)
        .build()
    style.addSource(source)

    val fillLayer = FillLayer("geo-shape-fill-layer", "geo-shape-source")
    fillLayer.fillColor(android.graphics.Color.parseColor("#00BCD4"))
    fillLayer.fillOpacity(0.3)
    style.addLayer(fillLayer)

    val lineLayer = LineLayer("geo-shape-line-layer", "geo-shape-source")
    lineLayer.lineColor(android.graphics.Color.parseColor("#00BCD4"))
    lineLayer.lineWidth(2.0)
    style.addLayer(lineLayer)
}

private fun zoomToFit(mapboxMap: com.mapbox.maps.MapboxMap, geoValue: GeoValue) {
    val coordinates = geoValue.coordinates
    if (coordinates.isEmpty()) return

    if (coordinates.size == 1) {
        val coord = coordinates.first()
        mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(coord.lng, coord.lat))
                .zoom(16.0)
                .build()
        )
        return
    }

    var minLat = Double.MAX_VALUE
    var maxLat = -Double.MAX_VALUE
    var minLng = Double.MAX_VALUE
    var maxLng = -Double.MAX_VALUE

    for (coord in coordinates) {
        if (coord.lat < minLat) minLat = coord.lat
        if (coord.lat > maxLat) maxLat = coord.lat
        if (coord.lng < minLng) minLng = coord.lng
        if (coord.lng > maxLng) maxLng = coord.lng
    }

    val centerLat = (minLat + maxLat) / 2
    val centerLng = (minLng + maxLng) / 2

    val latSpan = maxLat - minLat
    val lngSpan = maxLng - minLng
    val maxSpan = maxOf(latSpan, lngSpan)

    val zoom = when {
        maxSpan > 10 -> 4.0
        maxSpan > 1 -> 8.0
        maxSpan > 0.1 -> 12.0
        maxSpan > 0.01 -> 14.0
        maxSpan > 0.001 -> 16.0
        else -> 18.0
    }

    mapboxMap.setCamera(
        CameraOptions.Builder()
            .center(Point.fromLngLat(centerLng, centerLat))
            .zoom(zoom)
            .build()
    )
}
