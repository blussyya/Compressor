package com.karaza.squish.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.karaza.squish.data.CompressionPlan
import com.karaza.squish.data.UiState
import com.karaza.squish.ui.formatElapsed
import com.karaza.squish.ui.formatKbps

@Composable
fun ProgressScreen(
    state: UiState.Compressing,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                val bitmap = state.thumbnail
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().alpha(0.4f),
                        contentScale = ContentScale.Crop,
                    )
                }
                CircularProgressIndicator()
            }
        }

        androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))

        Text(
            text = if (state.isPassThrough) "Already under the limit — copying…" else subtitleFor(state.plan),
            style = MaterialTheme.typography.titleMedium,
        )

        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))

        if (!state.isPassThrough) {
            if (state.progress in 0..100) {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))

            val etaText = etaFor(state.progress, state.elapsedMs)
            Text(
                text = if (etaText != null) "${formatElapsed(state.elapsedMs)} elapsed · ~$etaText left" else "${formatElapsed(state.elapsedMs)} elapsed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))

        if (!state.isPassThrough) {
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}

private fun subtitleFor(plan: CompressionPlan): String {
    val audioPart = if (plan.keepAudio) formatKbps(plan.audioBps) else "muted"
    return "${plan.height}p · ${formatKbps(plan.videoBps)} · $audioPart"
}

private fun etaFor(progress: Int, elapsedMs: Long): String? {
    if (progress <= 10) return null
    val totalMs = elapsedMs.toDouble() * 100.0 / progress
    val remainingMs = (totalMs - elapsedMs).toLong().coerceAtLeast(0)
    return formatElapsed(remainingMs)
}
