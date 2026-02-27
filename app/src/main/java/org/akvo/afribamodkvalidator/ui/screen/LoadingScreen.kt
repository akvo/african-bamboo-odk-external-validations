package org.akvo.afribamodkvalidator.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.akvo.afribamodkvalidator.data.repository.SyncProgress
import org.akvo.afribamodkvalidator.navigation.LoadingType
import org.akvo.afribamodkvalidator.ui.theme.AfriBamODKValidatorTheme
import org.akvo.afribamodkvalidator.ui.viewmodel.LoadingResult
import org.akvo.afribamodkvalidator.ui.viewmodel.LoadingViewModel

@Composable
fun LoadingScreen(
    loadingType: LoadingType,
    message: String,
    onDownloadComplete: (totalEntries: Int, latestDate: String) -> Unit,
    onResyncComplete: (added: Int, updated: Int, latestTimestamp: String) -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoadingViewModel = hiltViewModel()
) {
    val result by viewModel.loadingResult.collectAsStateWithLifecycle()
    val progress by viewModel.syncProgress.collectAsStateWithLifecycle()

    LaunchedEffect(loadingType) {
        viewModel.startLoading(loadingType)
    }

    LaunchedEffect(result) {
        when (val r = result) {
            is LoadingResult.DownloadSuccess -> {
                onDownloadComplete(r.totalEntries, r.latestSubmissionDate)
            }
            is LoadingResult.ResyncSuccess -> {
                onResyncComplete(r.addedRecords, r.updatedRecords, r.latestRecordTimestamp)
            }
            is LoadingResult.Error, LoadingResult.Loading -> { /* Handled in UI */ }
        }
    }

    when (val r = result) {
        is LoadingResult.Error -> {
            ErrorScreenContent(
                errorMessage = r.message,
                onRetry = { viewModel.retry(loadingType) },
                onBackToLogin = onBackToLogin,
                modifier = modifier
            )
        }
        else -> {
            LoadingScreenContent(
                message = message,
                syncProgress = progress,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun LoadingScreenContent(
    message: String,
    syncProgress: SyncProgress? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val downloading = syncProgress as? SyncProgress.Downloading
        if (downloading != null && downloading.total > 0) {
            val fraction = downloading.processed.toFloat() / downloading.total.toFloat()
            CircularProgressIndicator(progress = { fraction })
            Spacer(modifier = Modifier.height(16.dp))
            val percent = (fraction * 100).toInt()
            Text(
                text = "$percent% (${downloading.processed}/${downloading.total})",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorScreenContent(
    errorMessage: String,
    onRetry: () -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onBackToLogin) {
            Text("Back to Login")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingScreenPreview() {
    AfriBamODKValidatorTheme {
        LoadingScreenContent(
            message = "Downloading data...",
            syncProgress = null
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingScreenWithProgressPreview() {
    AfriBamODKValidatorTheme {
        LoadingScreenContent(
            message = "Downloading data...",
            syncProgress = SyncProgress.Downloading(processed = 600, total = 1000)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorScreenPreview() {
    AfriBamODKValidatorTheme {
        ErrorScreenContent(
            errorMessage = "Unable to connect to server. Please check your internet connection and try again.",
            onRetry = {},
            onBackToLogin = {}
        )
    }
}
