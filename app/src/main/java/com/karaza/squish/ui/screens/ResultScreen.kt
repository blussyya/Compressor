package com.karaza.squish.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.karaza.squish.data.UiState
import com.karaza.squish.ui.formatMb

@Composable
fun ResultScreen(
    state: UiState.Done,
    onShareAgain: () -> Unit,
    onTryAgain: () -> Unit,
    onSaveToGallery: () -> Unit,
) {
    val savedPercent = if (state.sourceBytes > 0) {
        (100.0 * (1.0 - state.outputBytes.toDouble() / state.sourceBytes)).coerceAtLeast(0.0)
    } else 0.0

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onSaveToGallery() }

    fun requestSaveToGallery() {
        val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            onSaveToGallery()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Done", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Spacer()

        Text(
            "${formatMb(state.sourceBytes)} → ${formatMb(state.outputBytes)}",
            style = MaterialTheme.typography.titleLarge,
        )
        if (savedPercent > 0) {
            Text(
                "%.0f%% smaller".format(savedPercent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer()

        val plan = state.plan
        if (plan != null) {
            Text(
                "${plan.height}p · ${if (plan.keepAudio) "audio kept" else "muted"}",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text("Passed through unchanged", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(height = 24.dp)

        TextButton(onClick = ::requestSaveToGallery, enabled = !state.savingToGallery && !state.savedToGallery) {
            Text(
                when {
                    state.savedToGallery -> "Saved to gallery ✓"
                    state.savingToGallery -> "Saving…"
                    else -> "Save to gallery"
                }
            )
        }
        if (state.galleryError != null) {
            Text(
                "Couldn't save: ${state.galleryError}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(height = 12.dp)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onTryAgain, modifier = Modifier.weight(1f)) {
                Text("Try again")
            }
            Button(onClick = onShareAgain, modifier = Modifier.weight(1f)) {
                Text("Share again")
            }
        }
    }
}

@Composable
private fun Spacer(height: androidx.compose.ui.unit.Dp = 8.dp) {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(height))
}
