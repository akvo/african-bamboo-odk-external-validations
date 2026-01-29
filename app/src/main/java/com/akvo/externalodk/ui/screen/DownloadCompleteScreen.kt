package com.akvo.externalodk.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.akvo.externalodk.ui.theme.ExternalODKTheme

@Composable
fun DownloadCompleteScreen(
    totalEntries: Int,
    latestSubmissionDate: String,
    onViewData: () -> Unit,
    onResyncData: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Download Complete",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Total form entries downloaded: $totalEntries",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Latest submission date: $latestSubmissionDate",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onViewData,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("View Data")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onResyncData,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Resync Data")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DownloadCompleteScreenPreview() {
    ExternalODKTheme {
        DownloadCompleteScreen(
            totalEntries = 42,
            latestSubmissionDate = "2026-01-21 09:30",
            onViewData = {},
            onResyncData = {}
        )
    }
}
