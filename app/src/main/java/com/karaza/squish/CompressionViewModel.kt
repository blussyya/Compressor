package com.karaza.squish

import android.app.Application
import android.content.Intent
import android.media.MediaCodecInfo
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.karaza.squish.data.CompressionPlan
import com.karaza.squish.data.CompressionPlanner
import com.karaza.squish.data.MediaProbe
import com.karaza.squish.data.MediaStoreSaver
import com.karaza.squish.data.ResolutionChoice
import com.karaza.squish.data.SourceInfo
import com.karaza.squish.data.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@UnstableApi
class CompressionViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private companion object {
        const val KEY_SOURCE_URI = "source_uri"
        const val KEY_TARGET_BYTES = "target_bytes"
        const val KEY_RESOLUTION_CHOICE = "resolution_choice"
    }

    private val appContext get() = getApplication<Application>()

    private val _uiState = MutableStateFlow<UiState>(UiState.Home)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var sourceUri: Uri?
        get() = savedStateHandle.get<Uri>(KEY_SOURCE_URI)
        set(value) { savedStateHandle[KEY_SOURCE_URI] = value }

    private var currentTargetBytes: Long
        get() = savedStateHandle.get<Long>(KEY_TARGET_BYTES) ?: CompressionPlanner.DEFAULT_TARGET_BYTES
        set(value) { savedStateHandle[KEY_TARGET_BYTES] = value }

    private var currentResolutionChoice: ResolutionChoice
        get() = savedStateHandle.get<String>(KEY_RESOLUTION_CHOICE)
            ?.let { runCatching { ResolutionChoice.valueOf(it) }.getOrNull() }
            ?: ResolutionChoice.AUTO
        set(value) { savedStateHandle[KEY_RESOLUTION_CHOICE] = value.name }

    private var currentSourceInfo: SourceInfo? = null
    private var currentThumbnail: android.graphics.Bitmap? = null

    private var transformer: Transformer? = null
    private var progressJob: Job? = null
    private var exportStartedAtMs = 0L
    private var currentOutputFile: File? = null

    init {
        // Covers process-death restore: the ViewModel is fresh but the URI survived
        // in the SavedStateHandle. If the grant is gone, loadSource() fails gracefully.
        if (sourceUri != null) {
            loadSource()
        }
    }

    /** Call once, only when the Activity is a fresh launch (savedInstanceState == null). */
    fun handleIntent(intent: Intent) {
        purgeSharedCache()
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        if (intent.action != Intent.ACTION_SEND || uri == null) {
            _uiState.value = UiState.Home
            return
        }
        sourceUri = uri
        loadSource()
    }

    /** The other way in: tapping "Pick a video" on the home screen (launcher entry point). */
    fun onVideoPicked(uri: Uri) {
        purgeSharedCache()
        try {
            appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // Not all providers grant persistable access; the URI still works for this
            // session, it just won't survive process death. loadSource() below still runs.
        }
        sourceUri = uri
        loadSource()
    }

    private fun loadSource() {
        val uri = sourceUri ?: run { _uiState.value = UiState.Home; return }
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                runCatching { MediaProbe.readSourceInfo(appContext, uri) }.getOrNull()
            }
            if (info == null || info.durationSeconds <= 0 || info.sizeBytes <= 0) {
                _uiState.value = UiState.Home
                return@launch
            }
            currentSourceInfo = info

            if (info.sizeBytes < currentTargetBytes) {
                doPassThrough(info)
                return@launch
            }

            val thumb = withContext(Dispatchers.IO) {
                runCatching { MediaProbe.extractThumbnail(appContext, uri) }.getOrNull()
            }
            currentThumbnail = thumb
            updateDecidingState()
        }
    }

    private fun updateDecidingState() {
        val info = currentSourceInfo ?: return
        val choice = currentResolutionChoice
        val keepAudioPlan = CompressionPlanner.plan(
            currentTargetBytes, info.durationSeconds, keepAudio = true, info.channelCount, info.height, choice,
        )
        val mutePlan = CompressionPlanner.plan(
            currentTargetBytes, info.durationSeconds, keepAudio = false, info.channelCount, info.height, choice,
        )
        _uiState.value = UiState.Deciding(
            sourceInfo = info,
            thumbnail = currentThumbnail,
            targetBytes = currentTargetBytes,
            resolutionChoice = choice,
            keepAudioPlan = keepAudioPlan,
            mutePlan = mutePlan,
        )
    }

    fun setTargetBytes(bytes: Long) {
        currentTargetBytes = bytes
        if (_uiState.value is UiState.Deciding) updateDecidingState()
    }

    fun setResolutionChoice(choice: ResolutionChoice) {
        currentResolutionChoice = choice
        if (_uiState.value is UiState.Deciding) updateDecidingState()
    }

    /** User's choice from the decision screen. Ignored if the source has no audio track. */
    fun startCompression(keepAudio: Boolean) {
        val info = currentSourceInfo ?: return
        val effectiveKeepAudio = keepAudio && info.hasAudio
        val plan = CompressionPlanner.plan(
            currentTargetBytes, info.durationSeconds, effectiveKeepAudio, info.channelCount, info.height, currentResolutionChoice,
        )
        launchExport(info, plan, audioAttempt = 0)
    }

    fun retryAtLowerQuality() {
        val state = _uiState.value as? UiState.Failed ?: return
        val loweredHeight = CompressionPlanner.nextLowerHeight(state.plan.height)
        val newPlan = state.plan.copy(height = loweredHeight)
        launchExport(state.sourceInfo, newPlan, audioAttempt = if (newPlan.keepAudio) 1 else 0)
    }

    fun muteAndRetry() {
        val state = _uiState.value as? UiState.Failed ?: return
        val newPlan = CompressionPlanner.plan(
            currentTargetBytes, state.sourceInfo.durationSeconds, keepAudio = false, channelCount = 0,
            sourceHeight = state.sourceInfo.height, resolutionChoice = currentResolutionChoice,
        )
        launchExport(state.sourceInfo, newPlan, audioAttempt = 0)
    }

    fun shareOriginalFromFailure() {
        val state = _uiState.value as? UiState.Failed ?: return
        doPassThrough(state.sourceInfo)
    }

    /** "Try again" on the result screen: back to the decision cards, settings intact. */
    fun tryAgain() {
        if (currentSourceInfo != null) updateDecidingState()
    }

    fun markShared() {
        _uiState.update { s -> if (s is UiState.Done) s.copy(autoShared = true) else s }
    }

    /**
     * Copies the output into the device's Movies collection. On API 23-28 the caller
     * must have already obtained WRITE_EXTERNAL_STORAGE before calling this — there's
     * no Activity here to request it from.
     */
    fun saveToGallery() {
        val state = _uiState.value as? UiState.Done ?: return
        _uiState.update { s -> if (s is UiState.Done) s.copy(savingToGallery = true, galleryError = null) else s }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { MediaStoreSaver.save(appContext, state.outputUri) }
            }
            _uiState.update { s ->
                if (s !is UiState.Done) return@update s
                result.fold(
                    onSuccess = { s.copy(savingToGallery = false, savedToGallery = true) },
                    onFailure = { e -> s.copy(savingToGallery = false, galleryError = e.message ?: "Couldn't save") },
                )
            }
        }
    }

    fun cancel() {
        progressJob?.cancel()
        transformer?.cancel()
        transformer = null
        currentOutputFile?.delete()
        currentOutputFile = null
        if (currentSourceInfo != null) updateDecidingState() else _uiState.value = UiState.Home
    }

    private fun launchExport(info: SourceInfo, plan: CompressionPlan, audioAttempt: Int) {
        val out = File(sharedDir(), "squished_${System.currentTimeMillis()}.mp4")
        currentOutputFile = out
        exportStartedAtMs = SystemClock.elapsedRealtime()
        _uiState.value = UiState.Compressing(
            sourceInfo = info,
            thumbnail = currentThumbnail,
            plan = plan,
            progress = -1,
            elapsedMs = 0,
        )

        val videoSettings = VideoEncoderSettings.Builder()
            .setBitrate(plan.videoBps)
            .setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            .build()

        val encoderFactoryBuilder = DefaultEncoderFactory.Builder(appContext)
            .setRequestedVideoEncoderSettings(videoSettings)
            .setEnableFallback(true)

        // Only request an explicit audio bitrate on the first attempt. This is what
        // forces Transformer to actually re-encode the audio (DefaultEncoderFactory
        // .audioNeedsEncoding() checks for this) instead of transmuxing it through
        // unchanged at whatever bitrate the source happened to use.
        if (plan.keepAudio && plan.audioBps > 0 && audioAttempt == 0) {
            encoderFactoryBuilder.setRequestedAudioEncoderSettings(
                AudioEncoderSettings.Builder()
                    .setBitrate(plan.audioBps)
                    .build()
            )
        }

        val item = EditedMediaItem.Builder(MediaItem.fromUri(info.uri))
            .setRemoveAudio(!plan.keepAudio)
            .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(plan.height))))
            .build()

        val t = Transformer.Builder(appContext)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoderFactoryBuilder.build())
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    progressJob?.cancel()
                    onExportCompleted(info, plan, out)
                }

                override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                    progressJob?.cancel()
                    onExportFailed(info, plan, out, exception, audioAttempt)
                }
            })
            .build()

        transformer = t
        t.start(item, out.absolutePath)
        pollProgress()
    }

    private fun onExportCompleted(info: SourceInfo, plan: CompressionPlan, out: File) {
        transformer = null
        currentOutputFile = null
        val outUri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.provider", out)
        _uiState.value = UiState.Done(
            sourceBytes = info.sizeBytes,
            outputBytes = out.length(),
            plan = plan,
            outputUri = outUri,
        )
    }

    private fun onExportFailed(
        info: SourceInfo,
        plan: CompressionPlan,
        out: File,
        exception: ExportException,
        audioAttempt: Int,
    ) {
        transformer = null
        out.delete()
        currentOutputFile = null

        if (plan.keepAudio && plan.audioBps > 0 && audioAttempt == 0) {
            // Some devices reject specific AAC bitrate/profile combos. Retry once with
            // no explicit audio encoder settings (device default), still keeping audio.
            // Never fall back to muting automatically — that silently drops the track
            // the user asked to keep.
            launchExport(info, plan, audioAttempt = 1)
            return
        }

        _uiState.value = UiState.Failed(
            sourceInfo = info,
            thumbnail = currentThumbnail,
            plan = plan,
            errorCode = exception.errorCode,
            message = exception.message ?: "Unknown error",
            audioEncodingFailed = plan.keepAudio,
        )
    }

    private fun doPassThrough(info: SourceInfo) {
        _uiState.value = UiState.Compressing(
            sourceInfo = info,
            thumbnail = currentThumbnail,
            plan = CompressionPlanner.plan(
                currentTargetBytes, info.durationSeconds, keepAudio = info.hasAudio, info.channelCount,
                sourceHeight = info.height, resolutionChoice = currentResolutionChoice,
            ),
            progress = -1,
            elapsedMs = 0,
            isPassThrough = true,
        )
        viewModelScope.launch {
            val outUri = withContext(Dispatchers.IO) {
                runCatching {
                    val out = File(sharedDir(), "original_${System.currentTimeMillis()}.mp4")
                    appContext.contentResolver.openInputStream(info.uri)?.use { input ->
                        out.outputStream().use { input.copyTo(it) }
                    }
                    out to FileProvider.getUriForFile(appContext, "${appContext.packageName}.provider", out)
                }.getOrNull()
            }
            if (outUri == null) {
                _uiState.value = UiState.Home
                return@launch
            }
            val (out, uri) = outUri
            _uiState.value = UiState.Done(
                sourceBytes = info.sizeBytes,
                outputBytes = out.length(),
                plan = null,
                outputUri = uri,
            )
        }
    }

    private fun pollProgress() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            val holder = ProgressHolder()
            while (isActive) {
                delay(400)
                val t = transformer ?: break
                val state = t.getProgress(holder)
                val elapsed = SystemClock.elapsedRealtime() - exportStartedAtMs
                _uiState.update { current ->
                    if (current is UiState.Compressing) {
                        val progress = if (state == Transformer.PROGRESS_STATE_AVAILABLE) holder.progress else -1
                        current.copy(progress = progress, elapsedMs = elapsed)
                    } else current
                }
            }
        }
    }

    private fun sharedDir() = File(appContext.cacheDir, "shared").apply { mkdirs() }

    private fun purgeSharedCache() {
        sharedDir().listFiles()?.forEach { it.delete() }
    }

    override fun onCleared() {
        progressJob?.cancel()
        transformer?.cancel()
        super.onCleared()
    }
}
