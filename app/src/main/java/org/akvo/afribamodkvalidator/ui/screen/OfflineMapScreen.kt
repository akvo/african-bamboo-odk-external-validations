package org.akvo.afribamodkvalidator.ui.screen

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.akvo.afribamodkvalidator.data.model.OfflineRegion
import org.akvo.afribamodkvalidator.data.repository.OfflineRegionRepository
import org.akvo.afribamodkvalidator.ui.theme.AfriBamODKValidatorTheme
import org.akvo.afribamodkvalidator.validation.OfflineTileManager
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import javax.inject.Inject

@HiltViewModel
class OfflineMapViewModel @Inject constructor(
    private val repository: OfflineRegionRepository
) : ViewModel() {

    private val _regions = MutableStateFlow<List<OfflineRegion>>(emptyList())
    val regions: StateFlow<List<OfflineRegion>> = _regions.asStateFlow()

    init {
        loadRegions()
    }

    private fun loadRegions() {
        _regions.value = repository.loadRegions()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OfflineMapViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val regions by viewModel.regions.collectAsStateWithLifecycle()

    var downloadingRegion by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var totalTiles by remember { mutableIntStateOf(0) }
    var downloadedTiles by remember { mutableIntStateOf(0) }
    var tileManager by remember { mutableStateOf<OfflineTileManager?>(null) }

    // Calculate estimated sizes for regions
    var regionsWithEstimates by remember { mutableStateOf<List<OfflineRegion>>(emptyList()) }

    LaunchedEffect(regions) {
        if (regions.isNotEmpty()) {
            regionsWithEstimates = withContext(Dispatchers.IO) {
                calculateEstimates(context, regions)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline Maps") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Download map tiles for offline use",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (downloadingRegion != null) {
                DownloadProgressCard(
                    regionName = downloadingRegion!!,
                    progress = downloadProgress,
                    downloadedTiles = downloadedTiles,
                    totalTiles = totalTiles,
                    onCancel = {
                        tileManager?.cancelDownload()
                        downloadingRegion = null
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (regionsWithEstimates.isEmpty()) {
                Text(
                    text = "No regions configured",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(regionsWithEstimates) { region ->
                        RegionCard(
                            region = region,
                            isDownloading = downloadingRegion == region.name,
                            onDownload = {
                                downloadingRegion = region.name
                                downloadProgress = 0f
                                totalTiles = 0
                                downloadedTiles = 0

                                tileManager = startDownload(
                                    context = context,
                                    region = region,
                                    onProgress = { downloaded, total ->
                                        downloadedTiles = downloaded
                                        if (total > 0) totalTiles = total
                                        downloadProgress = if (totalTiles > 0) {
                                            downloaded.toFloat() / totalTiles
                                        } else 0f
                                    },
                                    onComplete = {
                                        downloadingRegion = null
                                    },
                                    onFailed = {
                                        downloadingRegion = null
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadProgressCard(
    regionName: String,
    progress: Float,
    downloadedTiles: Int,
    totalTiles: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Downloading $regionName...",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$downloadedTiles / $totalTiles tiles",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun RegionCard(
    region: OfflineRegion,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = region.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${region.estimatedTiles} tiles (~${region.estimatedSizeMb} MB)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onDownload,
                enabled = !isDownloading
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Text("Download", modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

private fun calculateEstimates(context: Context, regions: List<OfflineRegion>): List<OfflineRegion> {
    Configuration.getInstance().load(
        context,
        context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
    )

    val mapView = MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
    }

    val tileManager = OfflineTileManager(mapView)

    return regions.map { region ->
        val tiles = tileManager.estimateTileCount(region.boundingBox)
        val sizeMb = (tiles * AVERAGE_TILE_SIZE_KB) / 1024
        region.copy(
            estimatedTiles = tiles,
            estimatedSizeMb = sizeMb
        )
    }.also {
        mapView.onDetach()
    }
}

private fun startDownload(
    context: Context,
    region: OfflineRegion,
    onProgress: (downloaded: Int, total: Int) -> Unit,
    onComplete: () -> Unit,
    onFailed: () -> Unit
): OfflineTileManager {
    Configuration.getInstance().load(
        context,
        context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
    )

    val mapView = MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
    }

    val tileManager = OfflineTileManager(mapView)

    tileManager.downloadRegion(
        context = context,
        boundingBox = region.boundingBox,
        callback = object : OfflineTileManager.DownloadCallback {
            override fun onStarted() {}

            override fun onTotalTilesCalculated(total: Int) {
                onProgress(0, total)
            }

            override fun onProgress(downloaded: Int, currentZoomLevel: Int) {
                onProgress(downloaded, 0)
            }

            override fun onComplete() {
                mapView.onDetach()
                onComplete()
            }

            override fun onFailed(errors: Int) {
                mapView.onDetach()
                onFailed()
            }
        }
    )

    return tileManager
}

private const val AVERAGE_TILE_SIZE_KB = 15

@Preview(showBackground = true)
@Composable
private fun RegionCardPreview() {
    AfriBamODKValidatorTheme {
        RegionCard(
            region = OfflineRegion(
                name = "Addis Ababa",
                boundingBox = BoundingBox(9.1, 38.9, 8.8, 38.6),
                estimatedTiles = 3500,
                estimatedSizeMb = 51
            ),
            isDownloading = false,
            onDownload = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DownloadProgressCardPreview() {
    AfriBamODKValidatorTheme {
        DownloadProgressCard(
            regionName = "Addis Ababa",
            progress = 0.45f,
            downloadedTiles = 450,
            totalTiles = 1000,
            onCancel = {}
        )
    }
}