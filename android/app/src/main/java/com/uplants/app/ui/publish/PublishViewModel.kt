package com.uplants.app.ui.publish

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uplants.app.R
import com.uplants.app.appContainer
import com.uplants.app.data.content.ContentRepository
import com.uplants.app.data.device.AudioRecorder
import com.uplants.app.data.device.DeviceLocation
import com.uplants.app.data.device.DeviceServices
import com.uplants.app.data.device.PickedMedia
import com.uplants.app.data.model.Category
import com.uplants.app.data.model.ContentTypes
import com.uplants.app.data.model.NewPost
import com.uplants.app.ui.common.UiText
import com.uplants.app.ui.common.toUiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

data class PublishUiState(
    val contentType: String = ContentTypes.PHOTO,
    val media: PickedMedia? = null,
    val isRecordingAudio: Boolean = false,
    val recordingSeconds: Int = 0,
    /** For uploads and text posts the location is optional; live recordings always attach it. */
    val attachLocation: Boolean = false,
    val location: DeviceLocation? = null,
    val isLocating: Boolean = false,
    val locationFailed: Boolean = false,
    val sensorData: JsonObject? = null,
    val isReadingSensors: Boolean = false,
    val title: String = "",
    val description: String = "",
    val plantSpecies: String = "",
    val tagsText: String = "",
    val categories: List<Category> = emptyList(),
    val categoryId: Int? = null,
    val errors: PublishErrors = PublishErrors(),
    val isPublishing: Boolean = false,
    val error: UiText? = null,
    /** Set when the post was created: the screen opens it. */
    val publishedPostId: String? = null,
) {
    val isLive: Boolean get() = media?.isLive == true
    val wantsLocation: Boolean get() = isLive || attachLocation
    val isBusy: Boolean get() = isPublishing || isRecordingAudio || isLocating || isReadingSensors
}

/**
 * Publish (requirements 10, 15, 18): pick a file from the device ("upload") or record one in the app
 * ("live": camera or microphone). Live recordings attach the GPS location and a sensor snapshot.
 */
class PublishViewModel(
    private val content: ContentRepository,
    private val device: DeviceServices,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PublishUiState())
    val uiState: StateFlow<PublishUiState> = _uiState.asStateFlow()

    private var recorder: AudioRecorder? = null
    private var recordingTimer: Job? = null
    private var pendingCaptureUri: Uri? = null

    init {
        viewModelScope.launch {
            content.categories()
                .onSuccess { categories -> _uiState.update { it.copy(categories = categories) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.toUiText()) } }
        }
    }

    fun selectContentType(type: String) {
        if (type == _uiState.value.contentType) return
        cancelRecording()
        _uiState.update {
            // A photo can't be a video post: drop the file and the data captured with it.
            it.copy(
                contentType = type,
                media = null,
                sensorData = null,
                location = if (it.attachLocation) it.location else null,
                errors = PublishErrors(),
            )
        }
    }

    // ---- Media ---------------------------------------------------------------

    /** URI for the camera app to save into. Call right before launching it. */
    fun prepareCapture(): Uri {
        val extension = if (_uiState.value.contentType == ContentTypes.VIDEO) "mp4" else "jpg"
        return device.newCaptureUri(extension).also { pendingCaptureUri = it }
    }

    /** The camera app finished: [saved] is false when the user backed out. */
    fun onCaptureResult(saved: Boolean) {
        val uri = pendingCaptureUri ?: return
        pendingCaptureUri = null
        if (!saved) return
        val fallback = if (_uiState.value.contentType == ContentTypes.VIDEO) "video/mp4" else "image/jpeg"
        setMedia(device.describe(uri, isLive = true, fallbackMime = fallback))
    }

    /** A file picked from the gallery / file manager; null when the picker was cancelled. */
    fun onFilePicked(uri: Uri?) {
        if (uri == null) return
        setMedia(device.describe(uri, isLive = false))
    }

    fun removeMedia() = _uiState.update {
        it.copy(media = null, sensorData = null, location = if (it.attachLocation) it.location else null)
    }

    fun startAudioRecording() {
        if (_uiState.value.isRecordingAudio) return
        val rec = device.newAudioRecorder()
        if (!rec.start()) {
            _uiState.update { it.copy(error = UiText.Res(R.string.publish_error_microphone)) }
            return
        }
        recorder = rec
        _uiState.update { it.copy(isRecordingAudio = true, recordingSeconds = 0, media = null, error = null) }
        recordingTimer = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                _uiState.update { it.copy(recordingSeconds = it.recordingSeconds + 1) }
            }
        }
        // Sensors are read while the recording happens.
        readSensors()
    }

    fun stopAudioRecording() {
        val file = recorder?.stop()
        recorder = null
        recordingTimer?.cancel()
        _uiState.update { it.copy(isRecordingAudio = false) }
        if (file == null) {
            _uiState.update { it.copy(error = UiText.Res(R.string.publish_error_recording_short)) }
            return
        }
        setMedia(device.describe(device.uriFor(file), isLive = true, fallbackMime = "audio/mp4"), readSensorsNow = false)
    }

    private fun cancelRecording() {
        recorder?.cancel()
        recorder = null
        recordingTimer?.cancel()
        _uiState.update { it.copy(isRecordingAudio = false, recordingSeconds = 0) }
    }

    private fun setMedia(media: PickedMedia?, readSensorsNow: Boolean = true) {
        if (media == null) {
            _uiState.update { it.copy(error = UiText.Res(R.string.publish_error_read_file)) }
            return
        }
        _uiState.update {
            it.copy(
                media = media,
                error = null,
                errors = it.errors.copy(media = null, location = null),
                sensorData = if (media.isLive) it.sensorData else null,
            )
        }
        if (media.isLive) {
            if (readSensorsNow) readSensors()
            locate()
        }
    }

    // ---- Location and sensors -----------------------------------------------

    /** Turning on "Add my location" for an upload or text post. */
    fun setAttachLocation(attach: Boolean) {
        _uiState.update { it.copy(attachLocation = attach, location = if (attach || it.isLive) it.location else null) }
        if (attach && _uiState.value.location == null) locate()
    }

    /** Called by the screen when the user refused the location permission. */
    fun onLocationPermissionDenied() = _uiState.update {
        it.copy(attachLocation = false, locationFailed = it.isLive, isLocating = false)
    }

    fun locate() {
        if (_uiState.value.isLocating) return
        _uiState.update { it.copy(isLocating = true, locationFailed = false) }
        viewModelScope.launch {
            val location = device.location.current()
            _uiState.update {
                it.copy(
                    location = location ?: it.location,
                    isLocating = false,
                    locationFailed = location == null,
                    errors = if (location != null) it.errors.copy(location = null) else it.errors,
                )
            }
        }
    }

    private fun readSensors() {
        _uiState.update { it.copy(isReadingSensors = true) }
        viewModelScope.launch {
            val data = device.sensors.snapshot()
            _uiState.update { it.copy(sensorData = data, isReadingSensors = false) }
        }
    }

    // ---- Form ------------------------------------------------------------------

    fun onTitleChange(v: String) = _uiState.update { it.copy(title = v.take(PublishValidator.MAX_TITLE), errors = it.errors.copy(title = null)) }
    fun onDescriptionChange(v: String) = _uiState.update { it.copy(description = v.take(PublishValidator.MAX_DESCRIPTION), errors = it.errors.copy(description = null)) }
    fun onSpeciesChange(v: String) = _uiState.update { it.copy(plantSpecies = v.take(100)) }
    fun onTagsChange(v: String) = _uiState.update { it.copy(tagsText = v, errors = it.errors.copy(tags = null)) }
    fun onCategorySelected(id: Int) = _uiState.update { it.copy(categoryId = id, errors = it.errors.copy(category = null)) }
    fun errorShown() = _uiState.update { it.copy(error = null) }

    fun publish() {
        val s = _uiState.value
        if (s.isBusy) return
        val errors = PublishValidator.validate(
            PublishInput(
                contentType = s.contentType,
                title = s.title,
                description = s.description,
                categoryId = s.categoryId,
                tagsText = s.tagsText,
                mediaMimeType = s.media?.mimeType,
                mediaSizeBytes = s.media?.sizeBytes,
                isLive = s.isLive,
                hasLocation = s.location != null,
            )
        )
        if (errors.hasErrors) {
            _uiState.update { it.copy(errors = errors, error = UiText.Res(R.string.publish_error_fix_fields)) }
            return
        }
        val location = s.location.takeIf { s.wantsLocation }
        val post = NewPost(
            categoryId = s.categoryId!!,
            contentType = s.contentType,
            source = if (s.isLive) "live" else "upload",
            title = s.title.trim(),
            description = s.description.trim().ifEmpty { null },
            plantSpecies = s.plantSpecies.trim().ifEmpty { null },
            tags = PublishValidator.parseTags(s.tagsText),
            durationSeconds = s.media?.durationSeconds,
            latitude = location?.latitude,
            longitude = location?.longitude,
            locationName = location?.name,
            sensorData = s.sensorData.takeIf { s.isLive },
        )
        _uiState.update { it.copy(isPublishing = true, error = null) }
        viewModelScope.launch {
            content.createPost(post, s.media.takeIf { s.contentType != ContentTypes.TEXT })
                .onSuccess { created -> _uiState.update { it.copy(isPublishing = false, publishedPostId = created.id) } }
                .onFailure { e -> _uiState.update { it.copy(isPublishing = false, error = e.toUiText()) } }
        }
    }

    override fun onCleared() {
        recorder?.cancel()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer
                PublishViewModel(container.contentRepository, container.device)
            }
        }
    }
}
