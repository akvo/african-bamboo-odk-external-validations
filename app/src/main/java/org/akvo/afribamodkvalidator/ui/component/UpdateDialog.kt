package org.akvo.afribamodkvalidator.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.akvo.afribamodkvalidator.ui.theme.AfriBamODKValidatorTheme

@Composable
fun UpdateDialog(
    version: String,
    currentVersion: String,
    releaseNotes: String?,
    apkSizeMb: String,
    isMetered: Boolean,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Available") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Version $version is available (Current: $currentVersion)",
                    style = MaterialTheme.typography.bodyLarge
                )

                if (!releaseNotes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "What's new:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = releaseNotes,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Download size: $apkSizeMb MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isMetered) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "You are on mobile data",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text("Update Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        },
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
private fun UpdateDialogPreview() {
    AfriBamODKValidatorTheme {
        UpdateDialog(
            version = "1.7",
            currentVersion = "1.6",
            releaseNotes = "- Bug fixes\n- New validation rules\n- Performance improvements",
            apkSizeMb = "12.3",
            isMetered = false,
            onUpdate = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun UpdateDialogMeteredPreview() {
    AfriBamODKValidatorTheme {
        UpdateDialog(
            version = "1.7",
            currentVersion = "1.6",
            releaseNotes = null,
            apkSizeMb = "12.3",
            isMetered = true,
            onUpdate = {},
            onDismiss = {}
        )
    }
}
