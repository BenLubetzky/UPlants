package com.uplants.app.data.device

import android.content.Context
import android.net.Uri
import java.io.File

/** Access to the phone's hardware (GPS, sensors, microphone) and local files, created once in AppContainer. */
class DeviceServices(private val context: Context) {
    val location = LocationReader(context)
    val sensors = SensorReader(context)

    fun newAudioRecorder() = AudioRecorder(context)

    /** A file + URI for the camera app to save a photo or video into. */
    fun newCaptureUri(extension: String) = MediaFiles.uriFor(context, MediaFiles.newCaptureFile(context, extension))

    fun uriFor(file: File): Uri = MediaFiles.uriFor(context, file)

    fun describe(uri: Uri, isLive: Boolean, fallbackMime: String? = null) =
        MediaFiles.describe(context, uri, isLive, fallbackMime)
}
