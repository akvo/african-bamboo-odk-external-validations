package org.akvo.afribamodkvalidator.ui.screen

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.akvo.afribamodkvalidator.validation.TilePreviewActivity
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
    val isOnline = rememberIsOnline(context)

    var downloadingRegion by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var totalResources by remember { mutableIntStateOf(0) }
    var downloadedResources by remember { mutableIntStateOf(0) }
    var offlineManager by remember { mutableStateOf<MapboxOfflineManager?>(null) }

    // Track download status per region
    var downloadStatus by remember { mutableStateOf<Map<String, DownloadResult>>(emptyMap()) }

    // Track selected region
    var selectedRegionName by remember { mutableStateOf<String?>(null) }

    // Calculate estimated sizes for regions
    var regionsWithEstimates by remember { mutableStateOf<List<OfflineRegion>>(emptyList()) }

    LaunchedEffect(regions) {
        if (regions.isNotEmpty()) {
            regionsWithEstimates = calculateEstimates(regions)
        }
    }

    // Auto-select first region if none selected
    LaunchedEffect(regionsWithEstimates) {
        if (selectedRegionName == null && regionsWithEstimates.isNotEmpty()) {
            selectedRegionName = regionsWithEstimates.first().name
        }
    }

    val selectedRegion = regionsWithEstimates.find { it.name == selectedRegionName }

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
        bottomBar = {
            if (selectedRegion != null) {
                FooterActions(
                    region = selectedRegion,
                    isDownloading = downloadingRegion != null,
                    isOnline = isOnline,
                    downloadResult = downloadStatus[selectedRegion.name],
                    onPreview = {
                        val bbox = selectedRegion.boundingBox
                        context.startActivity(
                            TilePreviewActivity.createIntent(
                                context = context,
                                regionName = selectedRegion.name,
                                north = bbox.latNorth,
                                south = bbox.latSouth,
                                east = bbox.lonEast,
                                west = bbox.lonWest
                            )
                        )
                    },
                    onDownload = {
                        downloadingRegion = selectedRegion.name
                        downloadProgress = 0f
                        totalResources = 0
                        downloadedResources = 0

                        offlineManager = startMapboxDownload(
                            context = context,
                            region = selectedRegion,
                            onProgress = { completed, total ->
                                downloadedResources = completed
                                totalResources = total
                                downloadProgress = if (total > 0) {
                                    completed.toFloat() / total
                                } else 0f
                            },
                            onComplete = {
                                downloadStatus = downloadStatus + (selectedRegion.name to DownloadResult(
                                    success = true,
                                    downloadedTiles = downloadedResources,
                                    totalTiles = totalResources
                                ))
                                downloadingRegion = null
                            },
                            onFailed = { errorMessage ->
                                downloadStatus = downloadStatus + (selectedRegion.name to DownloadResult(
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
                            isSelected = region.name == selectedRegionName,
                            downloadResult = downloadStatus[region.name],
                            onClick = { selectedRegionName = region.name }
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
    isSelected: Boolean,
    downloadResult: DownloadResult?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null,
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = region.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "~${region.estimatedSizeMb} MB (zoom ${MapboxOfflineManager.ZOOM_MIN}-${MapboxOfflineManager.ZOOM_MAX})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            downloadResult?.let { result ->
                Spacer(modifier = Modifier.height(4.dp))
                val statusText = when {
                    result.success -> "Downloaded successfully"
                    result.errorMessage != null -> result.errorMessage
                    else -> "Download failed"
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
    }
}

@Composable
private fun FooterActions(
    region: OfflineRegion,
    isDownloading: Boolean,
    isOnline: Boolean,
    downloadResult: DownloadResult?,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = region.name,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDownload,
                    enabled = isOnline && !isDownloading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    val buttonText = if (downloadResult != null) "Re-download" else "Download"
                    Text(buttonText, modifier = Modifier.padding(start = 4.dp))
                }
                if (downloadResult?.success == true) {
                    OutlinedButton(
                        onClick = onPreview,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null)
                        Text("Preview", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            if (!isOnline) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Offline - download requires internet",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun rememberIsOnline(context: Context): Boolean {
    val connectivityManager = remember {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    var isOnline by remember {
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
        mutableStateOf(
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        )
    }

    DisposableEffect(connectivityManager) {
        val availableNetworks = mutableSetOf<Network>()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                availableNetworks.add(network)
                isOnline = true
            }

            override fun onLost(network: Network) {
                availableNetworks.remove(network)
                isOnline = availableNetworks.isNotEmpty()
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)

        onDispose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    return isOnline
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
            isSelected = false,
            downloadResult = null,
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RegionCardSelectedPreview() {
    AfriBamODKValidatorTheme {
        RegionCard(
            region = OfflineRegion(
                name = "Addis Ababa",
                boundingBox = MapBoundingBox(9.1, 38.9, 8.8, 38.6),
                estimatedTiles = 3500,
                estimatedSizeMb = 175
            ),
            isSelected = true,
            downloadResult = DownloadResult(
                success = true,
                downloadedTiles = 3500,
                totalTiles = 3500
            ),
            onClick = {}
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
            isSelected = false,
            downloadResult = DownloadResult(
                success = false,
                downloadedTiles = 0,
                totalTiles = 1000,
                errorCount = 1,
                errorMessage = "Network error"
            ),
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FooterActionsPreview() {
    AfriBamODKValidatorTheme {
        FooterActions(
            region = OfflineRegion(
                name = "Addis Ababa",
                boundingBox = MapBoundingBox(9.1, 38.9, 8.8, 38.6),
                estimatedTiles = 3500,
                estimatedSizeMb = 175
            ),
            isDownloading = false,
            isOnline = true,
            downloadResult = DownloadResult(
                success = true,
                downloadedTiles = 3500,
                totalTiles = 3500
            ),
            onPreview = {},
            onDownload = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FooterActionsOfflinePreview() {
    AfriBamODKValidatorTheme {
        FooterActions(
            region = OfflineRegion(
                name = "Sidama",
                boundingBox = MapBoundingBox(7.1, 38.5, 6.7, 38.1),
                estimatedTiles = 2000,
                estimatedSizeMb = 100
            ),
            isDownloading = false,
            isOnline = false,
            downloadResult = null,
            onPreview = {},
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
