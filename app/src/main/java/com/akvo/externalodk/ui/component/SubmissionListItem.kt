package com.akvo.externalodk.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = submission.displayTitle,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = submission.syncedOnText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider()
    }
}

@Preview(showBackground = true)
@Composable
private fun SubmissionListItemWithInstanceNamePreview() {
    ExternalODKTheme {
        SubmissionListItem(
            submission = SubmissionUiModel(
                uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                displayTitle = "enum_009-SID-03-2026-01-23",
                syncedOnText = "Synced on Tue, Jan 21, 2026 at 09:30",
                submissionTimestamp = 1737452400000L,
                isSynced = true
            ),
            onClick = {},
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SubmissionListItemFallbackPreview() {
    ExternalODKTheme {
        SubmissionListItem(
            submission = SubmissionUiModel(
                uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                displayTitle = "2026-01-21 08:15",
                syncedOnText = "Synced on Tue, Jan 21, 2026 at 08:15",
                submissionTimestamp = 1737447300000L,
                isSynced = true
            ),
            onClick = {},
            modifier = Modifier.padding(8.dp)
        )
    }
}
