package com.uplants.app.ui.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uplants.app.R
import com.uplants.app.data.model.UserSummary
import com.uplants.app.ui.common.UserAvatar
import com.uplants.app.ui.common.asString

/** "Save to…": tick the favourite lists this post should be in, or create a new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveToListSheet(
    postId: String,
    onDismiss: () -> Unit,
    viewModel: SaveToListViewModel = viewModel(key = "save-$postId", factory = SaveToListViewModel.factory(postId)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.save_to_list), style = MaterialTheme.typography.titleLarge)
            state.message?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
            when {
                state.isLoading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> Column {
                    Text(state.error!!.asString(), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::load) { Text(stringResource(R.string.retry)) }
                }
                state.lists.isEmpty() -> Text(stringResource(R.string.lists_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(state.lists, key = { it.id }) { list ->
                        Row(
                            Modifier.fillMaxWidth().clickable { viewModel.toggle(list.id) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = list.containsPost,
                                onCheckedChange = { viewModel.toggle(list.id) },
                                enabled = list.id !in state.busyListIds,
                            )
                            Text(list.name, Modifier.weight(1f))
                            Icon(
                                if (list.isPublic) Icons.Filled.Public else Icons.Filled.Lock,
                                stringResource(if (list.isPublic) R.string.list_public else R.string.list_private),
                                Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.newListName,
                    onValueChange = viewModel::onNewListNameChange,
                    placeholder = { Text(stringResource(R.string.list_new_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = viewModel::createList,
                    enabled = state.newListName.isNotBlank() && !state.isCreating,
                ) { Text(stringResource(R.string.list_create)) }
            }
        }
    }
}

/** Share a post with a registered user: search by name, optional note, tap to send. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharePostSheet(
    postId: String,
    onDismiss: () -> Unit,
    onShared: (UserSummary) -> Unit,
    viewModel: SharePostViewModel = viewModel(key = "share-$postId", factory = SharePostViewModel.factory(postId)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.sentTo) { state.sentTo?.let(onShared) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.share_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text(stringResource(R.string.share_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNoteChange,
                placeholder = { Text(stringResource(R.string.share_note_hint)) },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
            when {
                state.isSearching -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
                state.query.isNotBlank() && state.results.isEmpty() -> Text(
                    stringResource(R.string.share_no_results),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(state.results, key = { it.id }) { user ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(enabled = state.sendingToId == null) { viewModel.shareWith(user) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UserAvatar(user)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(user.shownName, style = MaterialTheme.typography.titleSmall)
                                Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (state.sendingToId == user.id) {
                                CircularProgressIndicator(Modifier.size(24.dp))
                            } else {
                                Text(stringResource(R.string.share_send), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
