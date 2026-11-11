package com.uplants.app.ui.post

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.uplants.app.R
import com.uplants.app.data.model.Comment
import com.uplants.app.data.model.ContentTypes
import com.uplants.app.data.model.Post
import com.uplants.app.ui.common.AuthorRow
import com.uplants.app.ui.common.CountLabel
import com.uplants.app.ui.common.ErrorBox
import com.uplants.app.ui.common.LoadingBox
import com.uplants.app.ui.common.LoginRequiredDialog
import com.uplants.app.ui.common.MediaPlayer
import com.uplants.app.ui.common.PostTags
import com.uplants.app.ui.common.StarRatingBar
import com.uplants.app.ui.common.UiText
import com.uplants.app.ui.common.UserAvatar
import com.uplants.app.ui.common.asString
import com.uplants.app.ui.common.compactCount
import com.uplants.app.ui.common.formatRating
import com.uplants.app.ui.common.relativeTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

@Composable
fun PostDetailScreen(
    onBack: () -> Unit,
    onDeleted: (postId: String) -> Unit,
    onRequestLogin: () -> Unit,
    onOpenProfile: (String) -> Unit,
    viewModel: PostDetailViewModel = viewModel(factory = PostDetailViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.deleted) {
        if (state.deleted) state.post?.let { onDeleted(it.id) }
    }
    PostDetailContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onReact = viewModel::react,
        onRate = viewModel::rate,
        onCommentDraftChange = viewModel::onCommentDraftChange,
        onSendComment = viewModel::sendComment,
        onDeleteComment = viewModel::deleteComment,
        onLoadMoreComments = viewModel::loadMoreComments,
        onDeletePost = viewModel::deletePost,
        onShared = viewModel::onShared,
        onMessageShown = viewModel::messageShown,
        onRequestLogin = onRequestLogin,
        onOpenProfile = onOpenProfile,
    )
}

private enum class Sheet { SAVE, SHARE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailContent(
    state: PostDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onReact: (Int) -> Unit,
    onRate: (Int?) -> Unit,
    onCommentDraftChange: (String) -> Unit,
    onSendComment: () -> Unit,
    onDeleteComment: (String) -> Unit,
    onLoadMoreComments: () -> Unit,
    onDeletePost: () -> Unit,
    onShared: (UiText) -> Unit,
    onMessageShown: () -> Unit,
    onRequestLogin: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.asString()
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onMessageShown()
        }
    }

    var showLoginDialog by rememberSaveable { mutableStateOf(false) }
    var openSheet by rememberSaveable { mutableStateOf<Sheet?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // Visitors can look but not touch: every action that needs an account goes through this.
    val requireLogin: (() -> Unit) -> Unit = { action -> if (state.isLoggedIn) action() else showLoginDialog = true }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.post_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (state.post != null) {
                        IconButton(onClick = { requireLogin { openSheet = Sheet.SHARE } }) {
                            Icon(Icons.Filled.Share, stringResource(R.string.share))
                        }
                    }
                    if (state.isMine) OwnerMenu(onDelete = { confirmDelete = true })
                },
            )
        },
    ) { padding ->
        val post = state.post
        when {
            post == null && state.isLoading -> LoadingBox(Modifier.padding(padding))
            post == null -> ErrorBox(state.error ?: UiText.Res(R.string.error_server), onRetry, Modifier.padding(padding))
            else -> Column(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize().imePadding()) {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                    item(key = "post") {
                        PostBody(
                            post = post,
                            state = state,
                            onReact = { value -> requireLogin { onReact(value) } },
                            onRate = { stars -> requireLogin { onRate(stars) } },
                            onSave = { requireLogin { openSheet = Sheet.SAVE } },
                            onShare = { requireLogin { openSheet = Sheet.SHARE } },
                            onOpenProfile = onOpenProfile,
                        )
                    }
                    item(key = "comments-header") {
                        Text(
                            stringResource(R.string.comments_count, post.commentCount),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                        )
                    }
                    if (state.comments.isEmpty() && !state.commentsLoading && state.commentsError == null) {
                        item(key = "no-comments") {
                            Text(
                                stringResource(R.string.comments_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    items(state.comments, key = { it.id }) { comment ->
                        CommentItem(
                            comment = comment,
                            canDelete = comment.author?.id == state.currentUserId && state.currentUserId != null,
                            onDelete = { onDeleteComment(comment.id) },
                            onOpenProfile = onOpenProfile,
                        )
                    }
                    item(key = "comments-footer") {
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            when {
                                state.commentsLoading -> CircularProgressIndicator(Modifier.size(24.dp))
                                state.commentsError != null -> TextButton(onClick = onLoadMoreComments) {
                                    Text(state.commentsError.asString() + " · " + stringResource(R.string.retry))
                                }
                                !state.commentsEndReached -> TextButton(onClick = onLoadMoreComments) {
                                    Text(stringResource(R.string.comments_load_more))
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                if (state.isLoggedIn) {
                    CommentInput(state.commentDraft, state.isSendingComment, onCommentDraftChange, onSendComment)
                } else {
                    TextButton(onClick = onRequestLogin, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Text(stringResource(R.string.comments_login_to_comment))
                    }
                }
            }
        }
    }

    if (showLoginDialog) {
        LoginRequiredDialog(onDismiss = { showLoginDialog = false }, onLogin = { showLoginDialog = false; onRequestLogin() })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_post_title)) },
            text = { Text(stringResource(R.string.delete_post_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDeletePost() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    val post = state.post
    if (post != null) {
        when (openSheet) {
            Sheet.SAVE -> SaveToListSheet(post.id, onDismiss = { openSheet = null })
            Sheet.SHARE -> SharePostSheet(
                postId = post.id,
                onDismiss = { openSheet = null },
                onShared = { user ->
                    openSheet = null
                    onShared(UiText.Res(R.string.share_sent, listOf(user.username)))
                },
            )
            null -> Unit
        }
    }
}

@Composable
private fun OwnerMenu(onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete_post_title)) },
                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}

@Composable
private fun PostBody(
    post: Post,
    state: PostDetailUiState,
    onReact: (Int) -> Unit,
    onRate: (Int?) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AuthorRow(post.author, post.createdAt, Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = onOpenProfile)
        PostMedia(post)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(post.title, style = MaterialTheme.typography.headlineSmall)
            PostTags(post)
            post.plantSpecies?.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.post_species, it), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            post.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            if (post.tags.isNotEmpty()) {
                Text(
                    post.tags.joinToString("  ") { "#$it" },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CountLabel(Icons.Filled.Visibility, pluralStringResource(R.plurals.post_views, post.viewCount, compactCount(post.viewCount)))
                CountLabel(Icons.Filled.Share, pluralStringResource(R.plurals.post_shares, post.shareCount, compactCount(post.shareCount)))
            }
            ActionsRow(post, state.myReaction, onReact, onSave, onShare)
            RatingSection(post, state.myStars, onRate)
            RecordingDetails(post)
        }
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun PostMedia(post: Post) {
    val url = post.mediaUrl ?: return
    when (post.contentType) {
        ContentTypes.PHOTO -> AsyncImage(
            model = url,
            contentDescription = post.title,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        ContentTypes.VIDEO -> MediaPlayer(url, isAudio = false)
        ContentTypes.AUDIO -> MediaPlayer(url, isAudio = true, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

/** Like / dislike / save / share (requirements 12, 13, 14). */
@Composable
private fun ActionsRow(post: Post, myReaction: Int?, onReact: (Int) -> Unit, onSave: () -> Unit, onShare: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReactionButton(
            checked = myReaction == 1,
            onClick = { onReact(1) },
            count = post.likeCount,
            iconOn = Icons.Filled.ThumbUp,
            iconOff = Icons.Outlined.ThumbUp,
            label = stringResource(R.string.like),
        )
        ReactionButton(
            checked = myReaction == -1,
            onClick = { onReact(-1) },
            count = post.dislikeCount,
            iconOn = Icons.Filled.ThumbDown,
            iconOff = Icons.Outlined.ThumbDown,
            label = stringResource(R.string.dislike),
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSave) { Icon(Icons.Filled.BookmarkBorder, stringResource(R.string.save_to_list)) }
        IconButton(onClick = onShare) { Icon(Icons.Filled.Share, stringResource(R.string.share)) }
    }
}

@Composable
private fun ReactionButton(
    checked: Boolean,
    onClick: () -> Unit,
    count: Int,
    iconOn: ImageVector,
    iconOff: ImageVector,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledIconToggleButton(checked = checked, onCheckedChange = { onClick() }) {
            Icon(if (checked) iconOn else iconOff, label)
        }
        Text(compactCount(count), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun RatingSection(post: Post, myStars: Int?, onRate: (Int?) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (post.ratingCount > 0) pluralStringResource(R.plurals.rating_summary, post.ratingCount, formatRating(post.ratingAvg), post.ratingCount)
                else stringResource(R.string.rating_none),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(stringResource(R.string.rating_yours), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StarRatingBar(stars = myStars, onRate = onRate)
            if (myStars == 0) {
                Text(stringResource(R.string.rating_zero), style = MaterialTheme.typography.bodySmall)
            }
            Row {
                TextButton(onClick = { onRate(0) }) { Text(stringResource(R.string.rating_give_zero)) }
                if (myStars != null) TextButton(onClick = { onRate(null) }) { Text(stringResource(R.string.rating_remove)) }
            }
        }
    }
}

/** GPS location and sensor readings captured while recording (requirement 18). */
@Composable
private fun RecordingDetails(post: Post) {
    val hasLocation = post.latitude != null && post.longitude != null
    val sensors = post.sensorData?.takeIf { it.isNotEmpty() }
    if (!hasLocation && sensors == null && post.locationName == null) return
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (hasLocation || post.locationName != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        post.locationName?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                        if (hasLocation) {
                            Text(
                                String.format(Locale.US, "%.5f, %.5f", post.latitude, post.longitude),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (hasLocation) {
                        TextButton(onClick = {
                            val label = post.locationName ?: post.title
                            val uri = "geo:${post.latitude},${post.longitude}?q=${post.latitude},${post.longitude}(${Uri.encode(label)})".toUri()
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        }) { Text(stringResource(R.string.open_map)) }
                    }
                }
            }
            if (sensors != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Sensors, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sensor_data), style = MaterialTheme.typography.titleSmall)
                }
                sensors.forEach { (name, value) ->
                    Text(
                        "${sensorLabel(name)}: ${value.readable()}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** "light_lux" → "Light (lux)", "accelerometer" → "Accelerometer (m/s²)". Unknown keys are just capitalised. */
internal fun sensorLabel(key: String): String {
    val units = mapOf("lux" to "lux", "hpa" to "hPa", "accelerometer" to "m/s²", "gyroscope" to "rad/s")
    val parts = key.split('_')
    val unit = units[parts.last().lowercase()]
    val name = (if (unit != null && parts.size > 1) parts.dropLast(1) else parts)
        .joinToString(" ").replaceFirstChar { it.uppercase() }
    return if (unit != null) "$name ($unit)" else name
}

private fun JsonElement.readable(): String = when (this) {
    is JsonPrimitive -> content
    is JsonArray -> joinToString(", ") { it.readable() }
    is JsonObject -> entries.joinToString(", ") { (k, v) -> "$k ${v.readable()}" }
}

@Composable
private fun CommentItem(comment: Comment, canDelete: Boolean, onDelete: () -> Unit, onOpenProfile: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        UserAvatar(comment.author, size = 32.dp, modifier = Modifier.clickable(enabled = comment.author != null) {
            comment.author?.let { onOpenProfile(it.id) }
        })
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.author?.shownName ?: stringResource(R.string.deleted_user), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(6.dp))
                Text(relativeTime(comment.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(comment.body, style = MaterialTheme.typography.bodyMedium)
        }
        if (canDelete) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, stringResource(R.string.delete_comment), Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun CommentInput(draft: String, isSending: Boolean, onChange: (String) -> Unit, onSend: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = draft,
            onValueChange = onChange,
            placeholder = { Text(stringResource(R.string.comment_hint)) },
            maxLines = 4,
            enabled = !isSending,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        if (isSending) {
            CircularProgressIndicator(Modifier.size(32.dp))
        } else {
            IconButton(onClick = onSend, enabled = draft.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.comment_send), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
