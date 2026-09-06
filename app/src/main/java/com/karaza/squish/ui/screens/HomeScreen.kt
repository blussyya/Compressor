package com.karaza.squish.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Shown when the app is launched from its own icon rather than a share sheet. */
@Composable
fun HomeScreen(onVideoPicked: (Uri) -> Unit) {
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onVideoPicked(uri)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Squish", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer()
        Text(
            "Shrink a video to fit a size limit, then send it wherever you like.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(height = 32.dp)
        Button(
            onClick = { pickVideo.launch(arrayOf("video/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Pick a video")
        }
        Spacer(height = 8.dp)
        Text(
            "Or share a video to Squish from your gallery or files app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Spacer(height: androidx.compose.ui.unit.Dp = 8.dp) {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(height))
}
