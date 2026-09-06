package com.karaza.squish.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karaza.squish.data.CompressionPlan
import com.karaza.squish.data.CompressionPlanner
import com.karaza.squish.data.ResolutionChoice
import com.karaza.squish.data.UiState
import com.karaza.squish.ui.formatDuration
import com.karaza.squish.ui.formatKbps
import com.karaza.squish.ui.formatMb

@Composable
fun DecisionScreen(
    state: UiState.Deciding,
    onTargetBytesChange: (Long) -> Unit,
    onResolutionChange: (ResolutionChoice) -> Unit,
    onChoose: (keepAudio: Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Thumbnail(state, Modifier.fillMaxWidth().aspectRatio(16f / 9f))

        Spacer(16.dp)

        Text(
            text = state.sourceInfo.fileName,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${formatDuration(state.sourceInfo.durationSeconds)} · ${formatMb(state.sourceInfo.sizeBytes)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(16.dp)

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CompressionPlanner.TARGET_SIZE_OPTIONS_MB) { mb ->
                val bytes = mb * 1024L * 1024L
                FilterChip(
                    selected = state.targetBytes == bytes,
                    onClick = { onTargetBytesChange(bytes) },
                    label = { Text("$mb MB") },
                )
            }
        }

        Spacer(8.dp)

        CustomSizeField(onTargetBytesChange)

        Spacer(16.dp)

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ResolutionChoice.entries) { choice ->
                val label = if (choice == ResolutionChoice.ORIGINAL && state.sourceInfo.height > 0) {
                    "Original (${state.sourceInfo.height}p)"
                } else {
                    choice.label
                }
                FilterChip(
                    selected = state.resolutionChoice == choice,
                    onClick = { onResolutionChange(choice) },
                    label = { Text(label) },
                )
            }
        }

        Spacer(20.dp)

        if (state.sourceInfo.hasAudio) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AudioChoiceCard(
                    modifier = Modifier.weight(1f),
                    emoji = "🔊",
                    title = "Keep audio",
                    plan = state.keepAudioPlan,
                    onClick = { onChoose(true) },
                )
                AudioChoiceCard(
                    modifier = Modifier.weight(1f),
                    emoji = "🔇",
                    title = "Mute",
                    plan = state.mutePlan,
                    onClick = { onChoose(false) },
                )
            }
        } else {
            Text(
                text = "This video has no audio track.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(12.dp)
            Button(onClick = { onChoose(false) }, modifier = Modifier.fillMaxWidth()) {
                Text("Compress — ${state.mutePlan.height}p · ~${formatMb(state.mutePlan.estimatedBytes)}")
            }
        }
    }
}

@Composable
private fun CustomSizeField(onTargetBytesChange: (Long) -> Unit) {
    var text by remember { mutableStateOf("") }
    val mb = text.toLongOrNull()?.takeIf { it > 0 }

    fun submit() {
        mb?.let { onTargetBytesChange(it * 1024L * 1024L) }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter(Char::isDigit).take(4) },
            label = { Text("Custom size (MB)") },
            singleLine = true,
            modifier = Modifier.width(160.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        Button(onClick = ::submit, enabled = mb != null) {
            Text("Set")
        }
    }
}

@Composable
private fun Thumbnail(state: UiState.Deciding, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            val bitmap = state.thumbnail
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun AudioChoiceCard(
    modifier: Modifier,
    emoji: String,
    title: String,
    plan: CompressionPlan,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            Text("$emoji  $title", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(8.dp)
            val subtitle = if (plan.keepAudio) {
                "${plan.height}p · ${formatKbps(plan.audioBps)}"
            } else {
                "${plan.height}p"
            }
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            Text(
                "~${formatMb(plan.estimatedBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun Spacer(height: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(height))
}
