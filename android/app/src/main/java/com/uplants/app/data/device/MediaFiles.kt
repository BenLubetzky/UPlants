package com.uplants.app.data.device

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File

/** A photo, video or audio file chosen on the device or just recorded in the app. */
data class PickedMedia(
    val uri: Uri,
    val mimeType: String,
    val sizeBytes: Long,
    val fileName: String?,
    val durationSeconds: Int?,
    /** Recorded in the app (camera / microphone) rather than picked from the device. */
    val isLive: Boolean,
)

object MediaFiles {

    /** A new empty file in the app's cache for the camera or microphone to write into. */
    fun newCaptureFile(context: Context, extension: String): File {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        return File(dir, "capture_${System.currentTimeMillis()}.$extension")
    }

    /** content:// URI another app (the camera) can write to; see res/xml/file_paths.xml. */
    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    /** Reads type, size, name and (for video/audio) duration. Null if the file can't be read. */
    fun describe(context: Context, uri: Uri, isLive: Boolean, fallbackMime: String? = null): PickedMedia? {
        val resolver = context.contentResolver
        var name: String? = null
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0)
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        if (size <= 0) {
            size = runCatching { resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: -1
        }
        if (size <= 0) return null
        val mime = resolver.getType(uri)?.takeIf { it.contains('/') && !it.endsWith("/*") } ?: fallbackMime ?: return null
        val duration = if (mime.startsWith("video/") || mime.startsWith("audio/")) durationSeconds(context, uri) else null
        return PickedMedia(uri, mime, size, name, duration, isLive)
    }

    private fun durationSeconds(context: Context, uri: Uri): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { ((it + 500) / 1000).toInt() }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
