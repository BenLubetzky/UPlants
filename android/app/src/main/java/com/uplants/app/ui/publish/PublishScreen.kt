package com.uplants.app.ui.publish

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.uplants.app.R
import com.uplants.app.data.device.PickedMedia
import com.uplants.app.data.model.ContentTypes
import com.uplants.app.ui.common.LocalNetworkStatus
import com.uplants.app.ui.common.asString
import com.uplants.app.ui.common.contentTypeIcon
import com.uplants.app.ui.common.contentTypeLabel
import java.util.Locale

private val contentTypes = listOf(ContentTypes.PHOTO, ContentTypes.VIDEO, ContentTypes.AUDIO, ContentTypes.TEXT)

private val locationPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

@Composable
fun PublishScreen(
    onPublished: (postId: String) -> Unit,
    viewModel: PublishViewModel = viewModel(factory = PublishViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.publishedPostId) { state.publishedPostId?.let(onPublished) }

    // What to do once the location permission answer comes back.
    var afterLocationPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), viewModel::onCaptureResult)
    val captureVideo = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo(), viewModel::onCaptureResult)
    val pickVisual = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), viewModel::onFilePicked)
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent(), viewModel::onFilePicked)
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startAudioRecording()
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) afterLocationPermission?.invoke() else viewModel.onLocationPermissionDenied()
        afterLocationPermission = null
    }

    /** Live recordings need GPS: ask for location first, then run [action]. */
    fun withLocationPermission(action: () -> Unit) {
        afterLocationPermission = action
        locationPermission.launch(locationPermissions)
    }

    PublishContent(
        state = state,
        onTypeSelected = viewModel::selectContentType,
        onRecordLive = {
            withLocationPermission {
                when (state.contentType) {
                    ContentTypes.PHOTO -> takePicture.launch(viewModel.prepareCapture())
                    ContentTypes.VIDEO -> captureVideo.launch(viewModel.prepareCapture())
                    ContentTypes.AUDIO -> microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        },
        onStopRecording = viewModel::stopAudioRecording,
        onPickFile = {
            when (state.contentType) {
                ContentTypes.PHOTO -> pickVisual.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                ContentTypes.VIDEO -> pickVisual.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                ContentTypes.AUDIO -> pickAudio.launch("audio/*")
            }
        },
        onRemoveMedia = viewModel::removeMedia,
        onAttachLocationChange = { attach ->
            if (attach) withLocationPermission { viewModel.setAttachLocation(true) } else viewModel.setAttachLocation(false)
        },
        onRetryLocation = { withLocationPermission(viewModel::locate) },
        onTitleChange = viewModel::onTitleChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onSpeciesChange = viewModel::onSpeciesChange,
        onTagsChange = viewModel::onTagsChange,
        onCategorySelected = viewModel::onCategorySelected,
        onPublish = viewModel::publish,
        onErrorShown = viewModel::errorShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishContent(
    state: PublishUiState,
    onTypeSelected: (String) -> Unit,
    onRecordLive: () -> Unit,
    onStopRecording: () -> Unit,
    onPickFile: () -> Unit,
    onRemoveMedia: () -> Unit,
    onAttachLocationChange: (Boolean) -> Unit,
    onRetryLocation: () -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSpeciesChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onCategorySelected: (Int) -> Unit,
    onPublish: () -> Unit,
    onErrorShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val error = state.error?.asString()
    LaunchedEffect(error) {
        if (error != null) {
            snackbar.showSnackbar(error)
            onErrorShown()
        }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.publish_title)) }) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.publish_what), style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                contentTypes.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = state.contentType == type,
                        onClick = { onTypeSelected(type) },
                        shape = SegmentedButtonDefaults.itemShape(index, contentTypes.size),
                        enabled = !state.isBusy,
                        icon = { Icon(contentTypeIcon(type), null, Modifier.size(18.dp)) },
                        label = { Text(contentTypeLabel(type), maxLines = 1) },
                    )
                }
            }

            if (state.contentType != ContentTypes.TEXT) {
                MediaSection(state, onRecordLive, onStopRecording, onPickFile, onRemoveMedia)
            }
            DeviceDataSection(state, onAttachLocationChange, onRetryLocation)

            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.publish_field_title)) },
                isError = state.errors.title != null,
                supportingText = state.errors.title?.let { { Text(stringResource(it)) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            CategoryField(state, onCategorySelected)
            OutlinedTextField(
                value = state.description,
                onValueChange = onDescriptionChange,
                label = { Text(stringResource(R.string.publish_field_description)) },
                isError = state.errors.description != null,
                supportingText = state.errors.description?.let { { Text(stringResource(it)) } },
                minLines = if (state.contentType == ContentTypes.TEXT) 5 else 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.plantSpecies,
                onValueChange = onSpeciesChange,
                label = { Text(stringResource(R.string.publish_field_species)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.tagsText,
                onValueChange = onTagsChange,
                label = { Text(stringResource(R.string.publish_field_tags)) },
                placeholder = { Text(stringResource(R.string.publish_tags_hint)) },
                isError = state.errors.tags != null,
                supportingText = { Text(stringResource(state.errors.tags ?: R.string.publish_tags_help)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val online = LocalNetworkStatus.current.isOnline
            if (!online) Text(stringResource(R.string.publish_offline), color = MaterialTheme.colorScheme.error)
            Button(
                onClick = onPublish,
                enabled = !state.isBusy && online,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                if (state.isPublishing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = ButtonDefaults.buttonColors().disabledContentColor)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.publish_uploading))
                } else {
                    Text(stringResource(R.string.publish_button))
                }
            }
        }
    }
}

/** Record live / choose file, or the chosen file's preview. */
@Composable
private fun MediaSection(
    state: PublishUiState,
    onRecordLive: () -> Unit,
    onStopRecording: () -> Unit,
    onPickFile: () -> Unit,
    onRemoveMedia: () -> Unit,
) {
    val media = state.media
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                state.isRecordingAudio -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FiberManualRecord, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.publish_recording, formatDuration(state.recordingSeconds)),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(onClick = onStopRecording) {
                        Icon(Icons.Filled.Stop, null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.publish_stop))
                    }
                }
                media != null -> MediaPreview(media, state.contentType, onRemoveMedia, enabled = !state.isPublishing)
                else -> {
                    Text(
                        stringResource(R.string.publish_media_hint, contentTypeLabel(state.contentType).lowercase(Locale.getDefault())),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.errors.media?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRecordLive, modifier = Modifier.weight(1f)) {
                            Icon(liveIcon(state.contentType), null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(liveLabel(state.contentType)))
                        }
                        OutlinedButton(onClick = onPickFile, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.FolderOpen, null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.publish_choose_file))
                        }
                    }
                    Text(
                        stringResource(R.string.publish_live_explained),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaPreview(media: PickedMedia, contentType: String, onRemove: () -> Unit, enabled: Boolean) {
    Box {
        if (contentType == ContentTypes.PHOTO) {
            AsyncImage(
                model = media.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(contentTypeIcon(contentType), null, Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(media.fileName ?: contentTypeLabel(contentType), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(
                        listOfNotNull(media.durationSeconds?.let(::formatDuration), formatSize(media.sizeBytes)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        IconButton(onClick = onRemove, enabled = enabled, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(Icons.Filled.Close, stringResource(R.string.publish_remove_media))
        }
    }
    Text(
        stringResource(if (media.isLive) R.string.publish_source_live else R.string.publish_source_upload),
        style = MaterialTheme.typography.labelLarge,
        color = if (media.isLive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Location (always for live recordings, optional otherwise) and the sensor snapshot. */
@Composable
private fun DeviceDataSection(state: PublishUiState, onAttachLocationChange: (Boolean) -> Unit, onRetryLocation: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, null)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.publish_location), style = MaterialTheme.typography.titleSmall)
                    val location = state.location
                    val status = when {
                        !state.wantsLocation -> stringResource(R.string.publish_location_off)
                        state.isLocating -> stringResource(R.string.publish_locating)
                        location != null -> listOfNotNull(
                            location.name,
                            String.format(Locale.US, "%.4f, %.4f", location.latitude, location.longitude),
                        ).joinToString(" · ")
                        state.locationFailed -> stringResource(R.string.publish_location_failed)
                        else -> stringResource(R.string.publish_location_when_recorded)
                    }
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.errors.location?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
                when {
                    state.isLocating -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    state.wantsLocation && state.locationFailed -> TextButton(onClick = onRetryLocation) { Text(stringResource(R.string.retry)) }
                    !state.isLive -> Switch(checked = state.attachLocation, onCheckedChange = onAttachLocationChange, enabled = !state.isPublishing)
                }
            }
            if (state.isLive || state.isRecordingAudio) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Sensors, null)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.sensor_data), style = MaterialTheme.typography.titleSmall)
                        Text(
                            when {
                                state.isReadingSensors -> stringResource(R.string.publish_reading_sensors)
                                state.sensorData != null -> state.sensorData.keys.filter { it != "device" }
                                    .joinToString(", ") { it.substringBefore('_').replaceFirstChar(Char::uppercase) }
                                    .ifEmpty { stringResource(R.string.publish_no_sensors) }
                                else -> stringResource(R.string.publish_no_sensors)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryField(state: PublishUiState, onCategorySelected: (Int) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selected = state.categories.firstOrNull { it.id == state.categoryId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.publish_field_category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            isError = state.errors.category != null,
            supportingText = state.errors.category?.let { { Text(stringResource(it)) } },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onCategorySelected(category.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun liveIcon(type: String) = when (type) {
    ContentTypes.VIDEO -> Icons.Filled.Videocam
    ContentTypes.AUDIO -> Icons.Filled.Mic
    else -> Icons.Filled.CameraAlt
}

private fun liveLabel(type: String) = when (type) {
    ContentTypes.VIDEO -> R.string.publish_record_video
    ContentTypes.AUDIO -> R.string.publish_record_audio
    else -> R.string.publish_take_photo
}

private fun formatDuration(seconds: Int) = String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format(Locale.US, "%d KB", (bytes / 1024).coerceAtLeast(1))
}
