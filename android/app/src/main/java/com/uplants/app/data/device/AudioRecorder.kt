package com.uplants.app.data.device

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Records audio from the microphone into an .m4a file (AAC). Needs the RECORD_AUDIO permission. */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var output: File? = null

    val isRecording: Boolean get() = recorder != null

    /** Starts recording; false if the microphone couldn't be opened. */
    fun start(): Boolean {
        if (recorder != null) return true
        val file = MediaFiles.newCaptureFile(context, "m4a")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(128_000)
            mr.setAudioSamplingRate(44_100)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            output = file
            true
        } catch (e: Exception) {
            mr.release()
            file.delete()
            false
        }
    }

    /** Stops and returns the recording, or null if nothing usable was recorded (e.g. stopped instantly). */
    fun stop(): File? {
        val mr = recorder ?: return null
        val file = output
        val ok = runCatching { mr.stop() }.isSuccess
        mr.release()
        recorder = null
        output = null
        if (!ok) file?.delete()
        return file.takeIf { ok && it != null && it.length() > 0 }
    }

    fun cancel() {
        stop()?.delete()
    }
}
