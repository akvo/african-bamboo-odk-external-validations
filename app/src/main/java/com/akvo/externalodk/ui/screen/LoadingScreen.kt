package com.akvo.externalodk.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.akvo.externalodk.ui.theme.ExternalODKTheme
import kotlinx.coroutines.delay

@Composable
fun LoadingScreen(
    message: String,
    onLoadingComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Mock delay to simulate loading
    LaunchedEffect(Unit) {
        delay(2000L) // 2 seconds mock delay
        onLoadingComplete()
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingScreenPreview() {
    ExternalODKTheme {
        LoadingScreen(
            message = "Downloading data...",
            onLoadingComplete = {}
        )
    }
}
