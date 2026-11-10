package com.uplants.app.data.content

import android.content.ContentResolver
import android.net.Uri
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException

/** Streams a file straight from its content URI into the upload, instead of loading up to 50 MB into memory. */
class UriRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    mimeType: String,
    private val length: Long,
) : RequestBody() {

    private val type = mimeType.toMediaType()

    override fun contentType(): MediaType = type

    override fun contentLength(): Long = length

    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(uri) ?: throw IOException("Can't open $uri")
        input.source().use { sink.writeAll(it) }
    }
}
