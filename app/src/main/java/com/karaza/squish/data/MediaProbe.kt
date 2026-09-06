package com.karaza.squish.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns

/**
 * Blocking metadata reads. Callers are responsible for hopping to Dispatchers.IO —
 * nothing here is safe to call from the main thread.
 */
object MediaProbe {

    fun readSourceInfo(context: Context, uri: Uri): SourceInfo {
        val channelCount = readAudioChannelCount(context, uri)
        return SourceInfo(
            uri = uri,
            fileName = displayName(context, uri),
            durationSeconds = durationSeconds(context, uri),
            sizeBytes = sizeOf(context, uri),
            hasAudio = channelCount > 0,
            channelCount = channelCount.coerceAtLeast(1),
        )
    }

    fun extractThumbnail(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            if (Build.VERSION.SDK_INT >= 27) {
                retriever.getScaledFrameAtTime(
                    1_000_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    640,
                    360,
                )
            } else {
                @Suppress("DEPRECATION")
                retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun durationSeconds(context: Context, uri: Uri): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L) / 1000
        } catch (e: Exception) {
            0L
        } finally {
            r.release()
        }
    }

    fun sizeOf(context: Context, uri: Uri): Long = try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    } catch (e: Exception) {
        -1L
    }

    private fun displayName(context: Context, uri: Uri): String {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (col >= 0 && cursor.moveToFirst()) {
                            cursor.getString(col)?.let { return it }
                        }
                    }
            } catch (e: Exception) {
                // fall through to the path-based guess below
            }
        }
        return uri.lastPathSegment ?: "video.mp4"
    }

    /** Channel count of the first audio track, or 0 if the source has no audio at all. */
    private fun readAudioChannelCount(context: Context, uri: Uri): Int {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    return format.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 2)
                }
            }
            0
        } catch (e: Exception) {
            2
        } finally {
            extractor.release()
        }
    }
}
