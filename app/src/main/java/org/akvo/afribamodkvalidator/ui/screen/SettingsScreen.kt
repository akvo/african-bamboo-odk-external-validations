package org.akvo.afribamodkvalidator.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
        onBlurWarnThresholdChange = viewModel::updateBlurWarnThreshold,
        onBlurBlockThresholdChange = viewModel::updateBlurBlockThreshold,
        onOverlapThresholdChange = viewModel::updateOverlapThreshold,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onBlurWarnThresholdChange: (Double) -> Unit,
    onBlurBlockThresholdChange: (Double) -> Unit,
    onOverlapThresholdChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
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
                SettingsSection(title = "Image Quality Check") {
                    ThresholdSlider(
                        label = "Warn Threshold",
                        value = uiState.blurWarnThreshold,
                        valueRange = 20f..500f,
                        description = "Warn if blur score is below this value",
                        onValueChange = onBlurWarnThresholdChange
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    ThresholdSlider(
                        label = "Block Threshold",
                        value = uiState.blurBlockThreshold,
                        valueRange = 10f..200f,
                        description = "Always block if blur score is below this value",
                        onValueChange = onBlurBlockThresholdChange
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsSection(title = "Polygon Validation") {
                    ThresholdSlider(
                        label = "Overlap Threshold",
                        value = uiState.overlapThreshold,
                        valueRange = 1f..50f,
                        description = "Block polygon if overlap exceeds this percentage",
                        suffix = "%",
                        onValueChange = onOverlapThresholdChange
                    )
                }
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
private fun ThresholdSlider(
    label: String,
    value: Double,
    valueRange: ClosedFloatingPointRange<Float>,
    description: String,
    suffix: String = "",
    onValueChange: (Double) -> Unit
) {
    var localValue by remember(value) { mutableFloatStateOf(value.toFloat()) }

    Column {
        Text(
            text = "$label: ${"%.0f".format(localValue.toDouble())}$suffix",
            style = MaterialTheme.typography.bodyLarge
        )
        Slider(
            value = localValue,
            onValueChange = { localValue = it },
            onValueChangeFinished = { onValueChange(localValue.toDouble()) },
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    AfriBamODKValidatorTheme {
        SettingsContent(
            uiState = SettingsUiState(isLoading = false),
            onBack = {},
            onBlurWarnThresholdChange = {},
            onBlurBlockThresholdChange = {},
            onOverlapThresholdChange = {}
        )
    }
}
