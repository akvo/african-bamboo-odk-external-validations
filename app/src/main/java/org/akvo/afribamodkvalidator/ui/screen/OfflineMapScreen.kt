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
import org.akvo.afribamodkvalidator.data.model.MapBoundingBox
import org.akvo.afribamodkvalidator.data.model.OfflineRegion
import org.akvo.afribamodkvalidator.data.repository.OfflineRegionRepository
import org.akvo.afribamodkvalidator.ui.theme.AfriBamODKValidatorTheme
import org.akvo.afribamodkvalidator.validation.MapboxOfflineManager
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
    var totalResources by remember { mutableIntStateOf(0) }
    var downloadedResources by remember { mutableIntStateOf(0) }
    var offlineManager by remember { mutableStateOf<MapboxOfflineManager?>(null) }

    // Track download status per region
    var downloadStatus by remember { mutableStateOf<Map<String, DownloadResult>>(emptyMap()) }

    // Calculate estimated sizes for regions
    var regionsWithEstimates by remember { mutableStateOf<List<OfflineRegion>>(emptyList()) }

    LaunchedEffect(regions) {
        if (regions.isNotEmpty()) {
            regionsWithEstimates = calculateEstimates(regions)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline Maps (Satellite)") },
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
                text = "Download satellite map tiles for offline use",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (downloadingRegion != null) {
                DownloadProgressCard(
                    regionName = downloadingRegion!!,
                    progress = downloadProgress,
                    downloadedResources = downloadedResources,
                    totalResources = totalResources,
                    onCancel = {
                        offlineManager?.cancelDownload()
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
                            isDownloading = downloadingRegion != null,
                            downloadResult = downloadStatus[region.name],
                            onDownload = {
                                downloadingRegion = region.name
                                downloadProgress = 0f
                                totalResources = 0
                                downloadedResources = 0

                                offlineManager = startMapboxDownload(
                                    context = context,
                                    region = region,
                                    onProgress = { completed, total ->
                                        downloadedResources = completed
                                        totalResources = total
                                        downloadProgress = if (total > 0) {
                                            completed.toFloat() / total
                                        } else 0f
                                    },
                                    onComplete = {
                                        downloadStatus = downloadStatus + (region.name to DownloadResult(
                                            success = true,
                                            downloadedTiles = downloadedResources,
                                            totalTiles = totalResources
                                        ))
                                        downloadingRegion = null
                                    },
                                    onFailed = { errorMessage ->
                                        downloadStatus = downloadStatus + (region.name to DownloadResult(
                                            success = false,
                                            downloadedTiles = downloadedResources,
                                            totalTiles = totalResources,
                                            errorCount = 1,
                                            errorMessage = errorMessage
                                        ))
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
    downloadedResources: Int,
    totalResources: Int,
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
                    text = "$downloadedResources / $totalResources resources",
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
    downloadResult: DownloadResult?,
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
                    text = "~${region.estimatedSizeMb} MB (zoom ${MapboxOfflineManager.ZOOM_MIN}-${MapboxOfflineManager.ZOOM_MAX})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Show download status if available
                downloadResult?.let { result ->
                    Spacer(modifier = Modifier.height(4.dp))
                    val statusText = when {
                        result.success -> "✓ Downloaded successfully"
                        result.errorMessage != null -> "✗ ${result.errorMessage}"
                        else -> "✗ Download failed"
                    }
                    val statusColor = when {
                        result.success -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )
                }
            }
            Button(
                onClick = onDownload,
                enabled = !isDownloading
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                val buttonText = if (downloadResult != null) "Re-download" else "Download"
                Text(buttonText, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

private fun calculateEstimates(regions: List<OfflineRegion>): List<OfflineRegion> {
    return regions.map { region ->
        // Estimate based on area and zoom levels
        // Satellite tiles are larger than street map tiles (~50KB avg vs 15KB)
        val bbox = region.boundingBox
        val latDiff = bbox.latNorth - bbox.latSouth
        val lonDiff = bbox.lonEast - bbox.lonWest
        val areaDegrees = latDiff * lonDiff

        // Rough estimate: ~100 tiles per 0.01 square degrees at zoom 15-18
        // Satellite tiles average ~50KB each
        val estimatedTiles = (areaDegrees * 10000 * 4).toInt() // 4 zoom levels
        val sizeMb = (estimatedTiles * AVERAGE_SATELLITE_TILE_SIZE_KB) / 1024

        region.copy(
            estimatedTiles = estimatedTiles,
            estimatedSizeMb = sizeMb.coerceAtLeast(1)
        )
    }
}

private fun startMapboxDownload(
    context: Context,
    region: OfflineRegion,
    onProgress: (completed: Int, total: Int) -> Unit,
    onComplete: () -> Unit,
    onFailed: (error: String) -> Unit
): MapboxOfflineManager {
    val manager = MapboxOfflineManager(context)

    val bbox = region.boundingBox
    val regionId = "region-${region.name.lowercase().replace(" ", "-")}"

    manager.downloadRegion(
        regionId = regionId,
        north = bbox.latNorth,
        south = bbox.latSouth,
        east = bbox.lonEast,
        west = bbox.lonWest,
        callback = object : MapboxOfflineManager.DownloadCallback {
            override fun onStarted() {}

            override fun onProgress(completed: Int, total: Int) {
                onProgress(completed, total)
            }

            override fun onComplete() {
                onComplete()
            }

            override fun onFailed(error: String) {
                onFailed(error)
            }

            override fun onCanceled() {
                onFailed("Download canceled")
            }
        }
    )

    return manager
}

private const val AVERAGE_SATELLITE_TILE_SIZE_KB = 50

/**
 * Represents the result of a download operation.
 */
data class DownloadResult(
    val success: Boolean,
    val downloadedTiles: Int,
    val totalTiles: Int,
    val errorCount: Int = 0,
    val errorMessage: String? = null
) {
    val isPartialSuccess: Boolean
        get() = !success && downloadedTiles > 0
}

@Preview(showBackground = true)
@Composable
private fun RegionCardPreview() {
    AfriBamODKValidatorTheme {
        RegionCard(
            region = OfflineRegion(
                name = "Addis Ababa",
                boundingBox = MapBoundingBox(9.1, 38.9, 8.8, 38.6),
                estimatedTiles = 3500,
                estimatedSizeMb = 175
            ),
            isDownloading = false,
            downloadResult = null,
            onDownload = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RegionCardDownloadedPreview() {
    AfriBamODKValidatorTheme {
        RegionCard(
            region = OfflineRegion(
                name = "Addis Ababa",
                boundingBox = MapBoundingBox(9.1, 38.9, 8.8, 38.6),
                estimatedTiles = 3500,
                estimatedSizeMb = 175
            ),
            isDownloading = false,
            downloadResult = DownloadResult(
                success = true,
                downloadedTiles = 3500,
                totalTiles = 3500
            ),
            onDownload = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RegionCardFailedPreview() {
    AfriBamODKValidatorTheme {
        RegionCard(
            region = OfflineRegion(
                name = "Pasunggingan",
                boundingBox = MapBoundingBox(-7.357, 109.514, -7.412, 109.429),
                estimatedTiles = 1000,
                estimatedSizeMb = 50
            ),
            isDownloading = false,
            downloadResult = DownloadResult(
                success = false,
                downloadedTiles = 0,
                totalTiles = 1000,
                errorCount = 1,
                errorMessage = "Network error"
            ),
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
            downloadedResources = 450,
            totalResources = 1000,
            onCancel = {}
        )
    }
}
