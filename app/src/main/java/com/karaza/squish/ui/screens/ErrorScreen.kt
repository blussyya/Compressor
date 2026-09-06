package com.karaza.squish.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karaza.squish.data.UiState

@Composable
fun ErrorScreen(
    state: UiState.Failed,
    onRetryLowerQuality: () -> Unit,
    onMuteAndRetry: () -> Unit,
    onShareOriginal: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Export failed",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))

        Text(
            "Code ${state.errorCode}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        androidx.compose.foundation.layout.Spacer(Modifier.height(32.dp))

        Button(onClick = onRetryLowerQuality, modifier = Modifier.fillMaxWidth()) {
            Text("Retry at lower quality")
        }

        if (state.audioEncodingFailed) {
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onMuteAndRetry, modifier = Modifier.fillMaxWidth()) {
                Text("Mute and retry")
            }
        }

        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
        TextButton(onClick = onShareOriginal, modifier = Modifier.fillMaxWidth()) {
            Text("Share original instead")
        }
    }
}
