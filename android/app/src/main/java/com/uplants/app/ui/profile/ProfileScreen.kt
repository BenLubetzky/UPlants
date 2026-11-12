package com.uplants.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Interests
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uplants.app.R
import com.uplants.app.data.model.FavoriteList
import com.uplants.app.data.model.Post
import com.uplants.app.data.model.Profile
import com.uplants.app.ui.common.ErrorBox
import com.uplants.app.ui.common.LoadMoreWhenNearEnd
import com.uplants.app.ui.common.LoadingBox
import com.uplants.app.ui.common.LoginRequiredDialog
import com.uplants.app.ui.common.PagedListFooter
import com.uplants.app.ui.common.PagedState
import com.uplants.app.ui.common.PostCard
import com.uplants.app.ui.common.UiText
import com.uplants.app.ui.common.UserAvatar
import com.uplants.app.ui.common.asString
import com.uplants.app.ui.common.compactCount
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * @param onBack null when shown as the "Profile" tab (your own profile).
 * @param onSignOut shown in the top bar on your own profile.
 */
@Composable
fun ProfileScreen(
    userId: String,
    onBack: (() -> Unit)?,
    onOpenPost: (String) -> Unit,
    onOpenFollowList: (userId: String, followers: Boolean) -> Unit,
    onEditInterests: () -> Unit,
    onOpenMyLists: () -> Unit,
    onOpenList: (String) -> Unit,
    onMessage: (userId: String, name: String) -> Unit,
    onOpenDashboard: () -> Unit,
    onSignOut: () -> Unit,
    onRequestLogin: () -> Unit,
    viewedPostId: String?,
    deletedPostId: String?,
    onPostChangesHandled: () -> Unit,
    viewModel: ProfileViewModel = viewModel(key = "profile-$userId", factory = ProfileViewModel.factory(userId)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    LaunchedEffect(viewedPostId, deletedPostId) {
        deletedPostId?.let(viewModel::removePost)
        viewedPostId?.takeIf { it != deletedPostId }?.let(viewModel::refreshPost)
        if (viewedPostId != null || deletedPostId != null) onPostChangesHandled()
    }
    ProfileContent(
        state = state,
        posts = posts,
        onBack = onBack,
        onRetry = viewModel::retry,
        onRefresh = viewModel::refresh,
        onLoadMorePosts = viewModel::loadMorePosts,
        onToggleFollow = viewModel::toggleFollow,
        onSaveProfile = viewModel::saveProfile,
        onProfileSavedHandled = viewModel::profileSavedHandled,
        onMessageShown = viewModel::messageShown,
        onOpenPost = onOpenPost,
        onOpenFollowList = onOpenFollowList,
        onEditInterests = onEditInterests,
        onOpenMyLists = onOpenMyLists,
        onOpenList = onOpenList,
        onMessage = onMessage,
        onOpenDashboard = onOpenDashboard,
        onSignOut = onSignOut,
        onRequestLogin = onRequestLogin,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileContent(
    state: ProfileUiState,
    posts: PagedState<Post>,
    onBack: (() -> Unit)?,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMorePosts: () -> Unit,
    onToggleFollow: () -> Unit,
    onSaveProfile: (displayName: String, bio: String) -> Unit,
    onProfileSavedHandled: () -> Unit,
    onMessageShown: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenFollowList: (userId: String, followers: Boolean) -> Unit,
    onEditInterests: () -> Unit,
    onOpenMyLists: () -> Unit,
    onOpenList: (String) -> Unit,
    onMessage: (userId: String, name: String) -> Unit,
    onOpenDashboard: () -> Unit,
    onSignOut: () -> Unit,
    onRequestLogin: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.asString()
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onMessageShown()
        }
    }
    var editing by rememberSaveable { mutableStateOf(false) }
    var showLoginDialog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.profileSaved) {
        if (state.profileSaved) {
            editing = false
            onProfileSavedHandled()
        }
    }

    Scaffold(
        // As a tab, the bottom navigation bar handles the bottom inset.
        contentWindowInsets = if (onBack == null) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.profile?.let { "@${it.username}" } ?: stringResource(R.string.profile_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                    }
                },
                actions = {
                    if (state.isMe) {
                        IconButton(onClick = onSignOut) { Icon(Icons.AutoMirrored.Filled.Logout, stringResource(R.string.logout)) }
                    }
                },
            )
        },
    ) { padding ->
        val profile = state.profile
        when {
            profile == null && state.isLoading -> LoadingBox(Modifier.padding(padding))
            profile == null -> ErrorBox(state.error ?: UiText.Res(R.string.error_server), onRetry, Modifier.padding(padding))
            else -> PullToRefreshBox(
                isRefreshing = false,
                onRefresh = onRefresh,
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                val listState = rememberLazyListState()
                LoadMoreWhenNearEnd(listState, onLoadMorePosts)
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "header") {
                        ProfileHeader(
                            profile = profile,
                            state = state,
                            onToggleFollow = { if (state.isLoggedIn) onToggleFollow() else showLoginDialog = true },
                            onEdit = { editing = true },
                            onEditInterests = onEditInterests,
                            onOpenMyLists = onOpenMyLists,
                            onOpenDashboard = onOpenDashboard,
                            onMessage = { if (state.isLoggedIn) onMessage(profile.id, profile.summary.shownName) else showLoginDialog = true },
                            onOpenFollowList = onOpenFollowList,
                        )
                    }
                    if (!state.isMe && state.lists.isNotEmpty()) {
                        item(key = "lists") { PublicLists(state.lists, onOpenList) }
                    }
                    item(key = "posts-title") {
                        Text(stringResource(R.string.profile_posts), style = MaterialTheme.typography.titleMedium)
                    }
                    when {
                        posts.isLoading -> item(key = "posts-loading") {
                            CircularProgressIndicator(Modifier.padding(16.dp).size(32.dp))
                        }
                        posts.error != null -> item(key = "posts-error") {
                            Column {
                                Text(posts.error.asString(), color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = onRefresh) { Text(stringResource(R.string.retry)) }
                            }
                        }
                        posts.items.isEmpty() -> item(key = "posts-empty") {
                            Text(
                                stringResource(if (state.isMe) R.string.profile_no_posts_me else R.string.profile_no_posts),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> {
                            items(posts.items, key = { it.id }) { post -> PostCard(post, onClick = { onOpenPost(post.id) }) }
                            item(key = "posts-footer") {
                                PagedListFooter(posts.isLoadingMore, posts.loadMoreError, posts.endReached, onRetry = onLoadMorePosts, endText = null)
                            }
                        }
                    }
                }
            }
        }
    }

    val profile = state.profile
    if (editing && profile != null) {
        EditProfileDialog(profile, isSaving = state.isSavingProfile, onSave = onSaveProfile, onDismiss = { editing = false })
    }
    if (showLoginDialog) {
        LoginRequiredDialog(onDismiss = { showLoginDialog = false }, onLogin = { showLoginDialog = false; onRequestLogin() })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileHeader(
    profile: Profile,
    state: ProfileUiState,
    onToggleFollow: () -> Unit,
    onEdit: () -> Unit,
    onEditInterests: () -> Unit,
    onOpenMyLists: () -> Unit,
    onOpenDashboard: () -> Unit,
    onMessage: () -> Unit,
    onOpenFollowList: (userId: String, followers: Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UserAvatar(profile.summary, size = 88.dp)
        Text(profile.summary.shownName, style = MaterialTheme.typography.headlineSmall)
        Text("@${profile.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        val bio = profile.bio?.takeIf { it.isNotBlank() }
        if (bio != null) {
            Text(bio, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        } else if (state.isMe) {
            Text(stringResource(R.string.profile_no_bio), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        joinedDate(profile.createdAt)?.let {
            Text(stringResource(R.string.profile_joined, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat(profile.postCount, stringResource(R.string.profile_stat_posts))
            Stat(profile.followerCount, stringResource(R.string.profile_stat_followers)) { onOpenFollowList(profile.id, true) }
            Stat(profile.followingCount, stringResource(R.string.profile_stat_following)) { onOpenFollowList(profile.id, false) }
        }
        if (state.isMe) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.profile_edit), Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = onEditInterests) {
                    Icon(Icons.Filled.Interests, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.interests_title), Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = onOpenMyLists) {
                    Icon(Icons.Filled.Bookmarks, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.lists_title), Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = onOpenDashboard) {
                    Icon(Icons.Filled.BarChart, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.dashboard_title), Modifier.padding(start = 6.dp))
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (profile.isFollowing == true) {
                    OutlinedButton(onClick = onToggleFollow, enabled = !state.isFollowBusy) { Text(stringResource(R.string.profile_following_button)) }
                } else {
                    Button(onClick = onToggleFollow, enabled = !state.isFollowBusy) { Text(stringResource(R.string.profile_follow)) }
                }
                OutlinedButton(onClick = onMessage) {
                    Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.profile_message), Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

/** Someone's public favourite lists. */
@Composable
private fun PublicLists(lists: List<FavoriteList>, onOpenList: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.lists_title), style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(lists, key = { it.id }) { list ->
                AssistChip(
                    onClick = { onOpenList(list.id) },
                    label = { Text(list.name + " (" + list.itemCount + ")") },
                    leadingIcon = { Icon(Icons.Filled.Bookmarks, null, Modifier.size(18.dp)) },
                )
            }
        }
    }
}

@Composable
private fun Stat(value: Int, label: String, onClick: (() -> Unit)? = null) {
    Column(
        Modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(compactCount(value), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EditProfileDialog(profile: Profile, isSaving: Boolean, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(profile.displayName.orEmpty()) }
    var bio by rememberSaveable { mutableStateOf(profile.bio.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(ProfileViewModel.MAX_NAME) },
                    label = { Text(stringResource(R.string.profile_field_name)) },
                    singleLine = true,
                    enabled = !isSaving,
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it.take(ProfileViewModel.MAX_BIO) },
                    label = { Text(stringResource(R.string.profile_field_bio)) },
                    supportingText = { Text("${bio.length} / ${ProfileViewModel.MAX_BIO}") },
                    minLines = 3,
                    enabled = !isSaving,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, bio) }, enabled = !isSaving) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** "November 2026" from an ISO timestamp. */
private fun joinedDate(iso: String): String? = runCatching {
    OffsetDateTime.parse(iso).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
}.getOrNull()
