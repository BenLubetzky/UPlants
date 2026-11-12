package com.uplants.app.ui.messages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uplants.app.R
import com.uplants.app.data.model.Conversation
import com.uplants.app.data.model.Message
import com.uplants.app.ui.common.EmptyBox
import com.uplants.app.ui.common.ErrorBox
import com.uplants.app.ui.common.LoadingBox
import com.uplants.app.ui.common.PostSummaryRow
import com.uplants.app.ui.common.UserAvatar
import com.uplants.app.ui.common.relativeTime
import com.uplants.app.ui.common.rememberMessageSnackbar
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onBack: () -> Unit,
    onOpenChat: (userId: String, name: String) -> Unit,
    viewModel: InboxViewModel = viewModel(factory = InboxViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.messages_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            state.isLoading -> LoadingBox(modifier)
            state.error != null -> ErrorBox(state.error!!, viewModel::load, modifier)
            state.conversations.isEmpty() -> EmptyBox(stringResource(R.string.messages_empty), modifier)
            else -> LazyColumn(modifier.fillMaxSize()) {
                items(state.conversations, key = { it.user.id }) { convo ->
                    ConversationRow(convo, onClick = { onOpenChat(convo.user.id, convo.user.shownName) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(convo: Conversation, onClick: () -> Unit) {
    val last = convo.lastMessage
    val unread = convo.unreadCount > 0
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(convo.user, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    convo.user.shownName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (unread) FontWeight.Bold else null,
                    modifier = Modifier.weight(1f),
                )
                Text(relativeTime(last.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    previewText(last),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (unread) FontWeight.SemiBold else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unread) Badge { Text(convo.unreadCount.toString()) }
            }
        }
    }
}

@Composable
private fun previewText(message: Message): String {
    val post = message.sharedPost
    return when {
        !message.body.isNullOrBlank() -> message.body
        post != null -> stringResource(R.string.messages_shared_post, post.title)
        message.sharedPostId != null -> stringResource(R.string.messages_shared_post_deleted)
        else -> ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    userId: String,
    name: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenPost: (String) -> Unit,
    viewModel: ChatViewModel = viewModel(key = "chat-$userId", factory = ChatViewModel.factory(userId)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = rememberMessageSnackbar(state.message, viewModel::messageShown)

    // While the chat is on screen, check for new messages every few seconds.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(POLL_MS)
                viewModel.refreshLatest()
            }
        }
    }

    var toDelete by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(name, modifier = Modifier.clickable { onOpenProfile(userId) }) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize().imePadding()) {
            Box(Modifier.weight(1f)) {
                when {
                    state.isLoading -> LoadingBox()
                    state.error != null -> ErrorBox(state.error!!, viewModel::load)
                    state.messages.isEmpty() -> EmptyBox(stringResource(R.string.chat_empty, name))
                    else -> MessageList(state, onLoadOlder = viewModel::loadOlder, onOpenPost = onOpenPost, onLongPressOwn = { toDelete = it })
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = viewModel::onDraftChange,
                    placeholder = { Text(stringResource(R.string.chat_hint)) },
                    maxLines = 4,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                if (state.isSending) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                } else {
                    IconButton(onClick = viewModel::send, enabled = state.draft.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.chat_send), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    toDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.chat_delete_title)) },
            text = { Text(stringResource(R.string.chat_delete_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(id); toDelete = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageList(
    state: ChatUiState,
    onLoadOlder: () -> Unit,
    onOpenPost: (String) -> Unit,
    onLongPressOwn: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    // Newest message at the bottom; scroll there when one arrives.
    LaunchedEffect(state.messages.lastOrNull()?.id) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(12.dp),
    ) {
        item(key = "older") {
            if (!state.olderEndReached) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (state.isLoadingOlder) CircularProgressIndicator(Modifier.size(24.dp))
                    else TextButton(onClick = onLoadOlder) { Text(stringResource(R.string.chat_load_older)) }
                }
            }
        }
        items(state.messages, key = { it.id }) { message ->
            val mine = message.senderId == state.currentUserId
            Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .combinedClickable(onClick = {}, onLongClick = { if (mine) onLongPressOwn(message.id) }),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        message.sharedPost?.let { post ->
                            PostSummaryRow(
                                post,
                                onClick = { onOpenPost(post.id) },
                                thumbnailSize = 48.dp,
                                // Long-press on the card works like on the rest of the bubble.
                                onLongClick = if (mine) ({ onLongPressOwn(message.id) }) else null,
                            )
                        }
                        if (message.sharedPost == null && message.sharedPostId != null) {
                            Text(stringResource(R.string.messages_shared_post_deleted), style = MaterialTheme.typography.bodySmall)
                        }
                        message.body?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        Text(
                            relativeTime(message.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.End),
                        )
                    }
                }
            }
        }
    }
}

private const val POLL_MS = 5_000L
