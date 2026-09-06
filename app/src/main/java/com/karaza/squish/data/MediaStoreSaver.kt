package com.karaza.squish.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Copies an already-compressed video (our own cache/shared/ file, reached via its
 * FileProvider Uri) into the device's public video collection so it shows up in the
 * gallery. Two paths because scoped storage split the API in two around Android 10.
 */
object MediaStoreSaver {

    /** Runs blocking I/O — call this from Dispatchers.IO. */
    fun save(context: Context, sourceUri: Uri): Uri {
        val displayName = "Squish_${System.currentTimeMillis()}.mp4"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveScoped(context, sourceUri, displayName)
        } else {
            saveLegacy(context, sourceUri, displayName)
        }
    }

    private fun saveScoped(context: Context, sourceUri: Uri, displayName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Squish")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val itemUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert failed")

        resolver.openOutputStream(itemUri)?.use { out ->
            resolver.openInputStream(sourceUri)?.use { it.copyTo(out) } ?: error("Could not open source")
        } ?: error("Could not open destination")

        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(itemUri, values, null, null)
        return itemUri
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, sourceUri: Uri, displayName: String): Uri {
        val moviesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Squish")
        moviesDir.mkdirs()
        val outFile = File(moviesDir, displayName)

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            outFile.outputStream().use { input.copyTo(it) }
        } ?: error("Could not open source")

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATA, outFile.absolutePath)
        }
        val itemUri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: Uri.fromFile(outFile)

        MediaScannerConnection.scanFile(context, arrayOf(outFile.absolutePath), arrayOf("video/mp4"), null)
        return itemUri
    }
}
