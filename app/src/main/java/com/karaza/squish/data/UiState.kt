package com.karaza.squish.data

import android.graphics.Bitmap
import android.net.Uri

sealed interface UiState {

    /** No share intent yet, or the restored source URI can no longer be read. */
    data object NoInput : UiState

    /** Reading source metadata / extracting the thumbnail. */
    data object Loading : UiState

    data class Deciding(
        val sourceInfo: SourceInfo,
        val thumbnail: Bitmap?,
        val targetBytes: Long,
        val keepAudioPlan: CompressionPlan,
        val mutePlan: CompressionPlan,
    ) : UiState

    data class Compressing(
        val sourceInfo: SourceInfo,
        val thumbnail: Bitmap?,
        val plan: CompressionPlan,
        /** 0..100, or -1 while indeterminate (not started, or unavailable). */
        val progress: Int,
        val elapsedMs: Long,
        val isPassThrough: Boolean = false,
    ) : UiState

    data class Done(
        val sourceBytes: Long,
        val outputBytes: Long,
        val plan: CompressionPlan?,
        val outputUri: Uri,
        val autoShared: Boolean = false,
    ) : UiState

    data class Failed(
        val sourceInfo: SourceInfo,
        val thumbnail: Bitmap?,
        val plan: CompressionPlan,
        val errorCode: Int,
        val message: String,
        val audioEncodingFailed: Boolean,
    ) : UiState
}
