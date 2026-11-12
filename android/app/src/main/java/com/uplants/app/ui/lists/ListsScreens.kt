package com.uplants.app.ui.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uplants.app.R
import com.uplants.app.data.model.FavoriteList
import com.uplants.app.ui.common.EmptyBox
import com.uplants.app.ui.common.ErrorBox
import com.uplants.app.ui.common.LoadMoreWhenNearEnd
import com.uplants.app.ui.common.LoadingBox
import com.uplants.app.ui.common.PagedListFooter
import com.uplants.app.ui.common.PostSummaryRow
import com.uplants.app.ui.common.rememberMessageSnackbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyListsScreen(
    onBack: () -> Unit,
    onOpenList: (String) -> Unit,
    viewModel: MyListsViewModel = viewModel(factory = MyListsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Reload whenever the screen is shown again: posts may have been saved meanwhile.
    LaunchedEffect(Unit) { viewModel.load() }
    val snackbar = rememberMessageSnackbar(state.message, viewModel::messageShown)
    var creating by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.created) {
        if (state.created) {
            creating = false
            viewModel.createdHandled()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lists_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.list_new)) },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            state.isLoading -> LoadingBox(modifier)
            state.error != null -> ErrorBox(state.error!!, viewModel::load, modifier)
            state.lists.isEmpty() -> EmptyBox(stringResource(R.string.lists_empty_screen), modifier)
            else -> LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                items(state.lists, key = { it.id }) { list ->
                    ListRow(list, onClick = { onOpenList(list.id) })
                    HorizontalDivider()
                }
            }
        }
    }
    if (creating) {
        NewListDialog(isBusy = state.isCreating, onCreate = viewModel::create, onDismiss = { creating = false })
    }
}

@Composable
fun ListRow(list: FavoriteList, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (list.isPublic) Icons.Filled.Public else Icons.Filled.Lock,
            stringResource(if (list.isPublic) R.string.list_public else R.string.list_private),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(list.name, style = MaterialTheme.typography.titleMedium)
            Text(
                pluralStringResource(R.plurals.list_post_count, list.itemCount, list.itemCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NewListDialog(isBusy: Boolean, onCreate: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var isPublic by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.list_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text(stringResource(R.string.list_new_hint)) },
                    singleLine = true,
                    enabled = !isBusy,
                )
                PublicSwitchRow(isPublic, enabled = !isBusy) { isPublic = it }
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(name, isPublic) }, enabled = name.isNotBlank() && !isBusy) { Text(stringResource(R.string.list_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PublicSwitchRow(isPublic: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.list_public_switch), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(if (isPublic) R.string.list_public_explained else R.string.list_private_explained),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = isPublic, onCheckedChange = onChange, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    listId: String,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    viewModel: ListDetailViewModel = viewModel(key = "list-$listId", factory = ListDetailViewModel.factory(listId)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    val snackbar = rememberMessageSnackbar(state.message, viewModel::messageShown)
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val header = state.header

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(header?.name ?: stringResource(R.string.lists_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    if (state.isOwner) {
                        var menu by remember { mutableStateOf(false) }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_rename)) },
                                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                onClick = { menu = false; renaming = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_delete)) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            posts.isLoading && header == null -> LoadingBox(modifier)
            posts.error != null && header == null -> ErrorBox(posts.error!!, viewModel::reload, modifier)
            else -> {
                val listState = rememberLazyListState()
                LoadMoreWhenNearEnd(listState, viewModel::loadMore)
                LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                    if (header != null) {
                        item(key = "header") {
                            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                header.owner?.let { owner ->
                                    Text(
                                        stringResource(R.string.list_by, owner.username),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { onOpenProfile(owner.id) },
                                    )
                                }
                                Text(
                                    pluralStringResource(R.plurals.list_post_count, header.itemCount, header.itemCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (state.isOwner) {
                                    PublicSwitchRow(header.isPublic, enabled = !state.isBusy, onChange = viewModel::setPublic)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                    if (posts.items.isEmpty() && !posts.isLoading) {
                        item(key = "empty") {
                            Text(
                                stringResource(if (state.isOwner) R.string.list_empty_owner else R.string.list_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }
                    items(posts.items, key = { it.post.id }) { saved ->
                        PostSummaryRow(
                            post = saved.post,
                            onClick = { onOpenPost(saved.post.id) },
                            trailing = if (state.isOwner) ({
                                IconButton(onClick = { viewModel.removePost(saved.post.id) }) {
                                    Icon(Icons.Filled.RemoveCircleOutline, stringResource(R.string.list_remove_post))
                                }
                            }) else null,
                        )
                    }
                    item(key = "footer") {
                        PagedListFooter(posts.isLoadingMore, posts.loadMoreError, posts.endReached, viewModel::loadMore, endText = null)
                    }
                }
            }
        }
    }

    if (renaming && header != null) {
        var name by rememberSaveable { mutableStateOf(header.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.list_rename)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, singleLine = true) },
            confirmButton = {
                Button(onClick = { viewModel.rename(name); renaming = false }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.list_delete)) },
            text = { Text(stringResource(R.string.list_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
