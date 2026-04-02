package org.akvo.afribamodkvalidator.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.akvo.afribamodkvalidator.ui.theme.AfriBamODKValidatorTheme
import org.akvo.afribamodkvalidator.ui.viewmodel.SettingsUiState
import org.akvo.afribamodkvalidator.ui.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsContent(
        uiState = uiState,
        onBack = onBack,
        onOcrWarnChange = viewModel::updateOcrWarnThreshold,
        onOcrBlockChange = viewModel::updateOcrBlockThreshold,
        onLapWarnChange = viewModel::updateLaplacianWarnThreshold,
        onLapBlockChange = viewModel::updateLaplacianBlockThreshold,
        onOverlapChange = viewModel::updateOverlapThreshold,
        onReset = viewModel::resetToDefaults,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onOcrWarnChange: (Double) -> Unit,
    onOcrBlockChange: (Double) -> Unit,
    onLapWarnChange: (Double) -> Unit,
    onLapBlockChange: (Double) -> Unit,
    onOverlapChange: (Double) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Settings") },
            text = { Text("Reset all thresholds to recommended values?") },
            confirmButton = {
                TextButton(onClick = {
                    onReset()
                    showResetDialog = false
                }) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                SettingsSection(title = "Image Quality Check (OCR)") {
                    CounterSlider(
                        label = "Warn Threshold",
                        value = uiState.ocrWarnThreshold,
                        valueRange = 0.1f..0.95f,
                        step = 0.05,
                        format = { "%.2f".format(it) },
                        description = "Warn if OCR confidence is below this value",
                        onValueChange = onOcrWarnChange
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    CounterSlider(
                        label = "Block Threshold",
                        value = uiState.ocrBlockThreshold,
                        valueRange = 0.05f..0.8f,
                        step = 0.05,
                        format = { "%.2f".format(it) },
                        description = "Always block if OCR confidence is below this value",
                        onValueChange = onOcrBlockChange
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsSection(title = "Image Quality Check (Laplacian)") {
                    CounterSlider(
                        label = "Warn Threshold",
                        value = uiState.laplacianWarnThreshold,
                        valueRange = 20f..500f,
                        step = 10.0,
                        format = { "%.0f".format(it) },
                        description = "Fallback for non-Latin text",
                        onValueChange = onLapWarnChange
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    CounterSlider(
                        label = "Block Threshold",
                        value = uiState.laplacianBlockThreshold,
                        valueRange = 10f..200f,
                        step = 10.0,
                        format = { "%.0f".format(it) },
                        description = "Always block below this Laplacian variance",
                        onValueChange = onLapBlockChange
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsSection(title = "Polygon Validation") {
                    CounterSlider(
                        label = "Overlap Threshold",
                        value = uiState.overlapThreshold,
                        valueRange = 1f..50f,
                        step = 1.0,
                        format = { "%.0f%%".format(it) },
                        description = "Block polygon if overlap exceeds this percentage",
                        onValueChange = onOverlapChange
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset Settings")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun CounterSlider(
    label: String,
    value: Double,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Double,
    format: (Double) -> String,
    description: String,
    onValueChange: (Double) -> Unit
) {
    Column {
        Text(
            text = "$label: ${format(value)}",
            style = MaterialTheme.typography.bodyLarge
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = {
                    val newVal = (value - step).coerceIn(
                        valueRange.start.toDouble(),
                        valueRange.endInclusive.toDouble()
                    )
                    onValueChange(roundToStep(newVal, step))
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease")
            }
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(roundToStep(it.toDouble(), step)) },
                valueRange = valueRange,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val newVal = (value + step).coerceIn(
                        valueRange.start.toDouble(),
                        valueRange.endInclusive.toDouble()
                    )
                    onValueChange(roundToStep(newVal, step))
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase")
            }
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun roundToStep(value: Double, step: Double): Double {
    return (value / step).roundToInt() * step
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    AfriBamODKValidatorTheme {
        SettingsContent(
            uiState = SettingsUiState(isLoading = false),
            onBack = {},
            onOcrWarnChange = {},
            onOcrBlockChange = {},
            onLapWarnChange = {},
            onLapBlockChange = {},
            onOverlapChange = {},
            onReset = {}
        )
    }
}
