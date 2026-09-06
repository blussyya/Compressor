package com.karaza.squish.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.karaza.squish.CompressionViewModel
import com.karaza.squish.data.UiState
import com.karaza.squish.ui.screens.DecisionScreen
import com.karaza.squish.ui.screens.ErrorScreen
import com.karaza.squish.ui.screens.HomeScreen
import com.karaza.squish.ui.screens.ProgressScreen
import com.karaza.squish.ui.screens.ResultScreen

@Composable
fun SquishApp(viewModel: CompressionViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state) {
        val s = state
        if (s is UiState.Done && !s.autoShared) {
            launchShareSheet(context, s.outputUri)
            viewModel.markShared()
        }
    }

    LaunchedEffect(state is UiState.Compressing) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        if (state is UiState.Compressing) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // So the export's foreground-service notification actually shows on API 33+.
    // Not gating anything on the result — the export runs either way, this just
    // affects whether progress is visible while backgrounded.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(state is UiState.Compressing) {
        if (state !is UiState.Compressing) return@LaunchedEffect
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is UiState.Home -> HomeScreen(onVideoPicked = viewModel::onVideoPicked)
                is UiState.Loading -> LoadingSpinner()
                is UiState.Deciding -> DecisionScreen(
                    state = s,
                    onTargetBytesChange = viewModel::setTargetBytes,
                    onResolutionChange = viewModel::setResolutionChoice,
                    onChoose = viewModel::startCompression,
                )
                is UiState.Compressing -> ProgressScreen(
                    state = s,
                    onCancel = viewModel::cancel,
                )
                is UiState.Done -> ResultScreen(
                    state = s,
                    onShareAgain = { launchShareSheet(context, s.outputUri) },
                    onTryAgain = viewModel::tryAgain,
                    onSaveToGallery = viewModel::saveToGallery,
                )
                is UiState.Failed -> ErrorScreen(
                    state = s,
                    onRetryLowerQuality = viewModel::retryAtLowerQuality,
                    onMuteAndRetry = viewModel::muteAndRetry,
                    onShareOriginal = viewModel::shareOriginalFromFailure,
                )
            }
        }
    }
}

@Composable
private fun LoadingSpinner() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

fun launchShareSheet(context: android.content.Context, uri: android.net.Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Send compressed video"))
}
