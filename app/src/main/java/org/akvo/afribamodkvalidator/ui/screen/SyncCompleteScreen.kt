package org.akvo.afribamodkvalidator.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.akvo.afribamodkvalidator.ui.theme.AfriBamODKValidatorTheme

@Composable
fun SyncCompleteScreen(
    addedRecords: Int,
    updatedRecords: Int,
    latestRecordTimestamp: String,
    onReturnToDashboard: () -> Unit,
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
            text = "Sync Complete",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Added Records: $addedRecords",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Updated Records: $updatedRecords",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Latest Record Timestamp: $latestRecordTimestamp",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onReturnToDashboard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Return to Dashboard")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SyncCompleteScreenPreview() {
    AfriBamODKValidatorTheme {
        SyncCompleteScreen(
            addedRecords = 5,
            updatedRecords = 3,
            latestRecordTimestamp = "2026-01-21 10:45",
            onReturnToDashboard = {}
        )
    }
}
