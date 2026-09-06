package com.karaza.squish.data

import android.net.Uri

/** Facts read off the shared video before any decision is made. */
data class SourceInfo(
    val uri: Uri,
    val fileName: String,
    val durationSeconds: Long,
    val sizeBytes: Long,
    val hasAudio: Boolean,
    val channelCount: Int,
    /** Display height in pixels, already corrected for rotation (portrait vs landscape). */
    val height: Int,
)
