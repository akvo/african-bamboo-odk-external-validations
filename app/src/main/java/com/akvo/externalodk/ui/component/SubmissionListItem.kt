package com.akvo.externalodk.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.akvo.externalodk.ui.model.SubmissionUiModel
import com.akvo.externalodk.ui.theme.ExternalODKTheme

@Composable
fun SubmissionListItem(
    submission: SubmissionUiModel,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = submission.submittedBy,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = submission.submissionTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = submission.uuid.take(8) + "...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusBadge(isSynced = submission.isSynced)
        }
        HorizontalDivider()
    }
}

@Composable
private fun StatusBadge(
    isSynced: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSynced) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val textColor = if (isSynced) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    val text = if (isSynced) "Synced" else "Pending"

    Surface(
        color = backgroundColor,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SubmissionListItemPreview() {
    ExternalODKTheme {
        SubmissionListItem(
            submission = SubmissionUiModel(
                uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                submittedBy = "ifirmawan",
                submissionTime = "2026-01-21 09:30",
                isSynced = true
            ),
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SubmissionListItemPendingPreview() {
    ExternalODKTheme {
        SubmissionListItem(
            submission = SubmissionUiModel(
                uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                submittedBy = "john.doe",
                submissionTime = "2026-01-21 08:15",
                isSynced = false
            ),
            modifier = Modifier.padding(8.dp)
        )
    }
}
