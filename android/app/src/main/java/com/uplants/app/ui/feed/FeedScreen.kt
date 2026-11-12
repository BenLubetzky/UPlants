package com.uplants.app.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uplants.app.R
import com.uplants.app.data.content.PostSort
import com.uplants.app.data.model.ContentTypes
import com.uplants.app.ui.common.EmptyBox
import com.uplants.app.ui.common.ErrorBox
import com.uplants.app.ui.common.LoadMoreWhenNearEnd
import com.uplants.app.ui.common.LoadingBox
import com.uplants.app.ui.common.LocalNetworkStatus
import com.uplants.app.ui.common.SavedContentNote
import com.uplants.app.ui.common.PagedListFooter
import com.uplants.app.ui.common.PostCard
import com.uplants.app.ui.common.asString
import com.uplants.app.ui.common.contentTypeLabel

@Composable
fun FeedScreen(
    isLoggedIn: Boolean,
    onOpenPost: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onPickInterests: () -> Unit,
    onOpenInbox: () -> Unit,
    deletedPostId: String?,
    onDeletedPostHandled: () -> Unit,
    refreshRequested: Boolean,
    onRefreshHandled: () -> Unit,
    viewedPostId: String?,
    onViewedPostHandled: () -> Unit,
    viewModel: FeedViewModel = viewModel(factory = FeedViewModel.factory(isLoggedIn)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshUnread() }
    // Back online while showing saved posts or an error: load fresh ones (requirement 23).
    val online = LocalNetworkStatus.current.isOnline
    LaunchedEffect(online) {
        if (online && (state.savedAt != null || state.error != null)) viewModel.refresh()
    }
    LaunchedEffect(deletedPostId) {
        if (deletedPostId != null) {
            viewModel.removePost(deletedPostId)
            onDeletedPostHandled()
        }
    }
    LaunchedEffect(viewedPostId) {
        if (viewedPostId != null) {
            viewModel.refreshPost(viewedPostId)
            onViewedPostHandled()
        }
    }
    LaunchedEffect(refreshRequested) {
        if (refreshRequested) {
            viewModel.refresh()
            onRefreshHandled()
        }
    }
    FeedContent(
        state = state,
        onTabSelected = viewModel::selectTab,
        onSortSelected = viewModel::selectSort,
        onTypeSelected = viewModel::selectType,
        onCategorySelected = viewModel::selectCategory,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onOpenPost = onOpenPost,
        onOpenProfile = onOpenProfile,
        onPickInterests = onPickInterests,
        onOpenInbox = onOpenInbox,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedContent(
    state: FeedUiState,
    onTabSelected: (FeedTab) -> Unit,
    onSortSelected: (PostSort) -> Unit,
    onTypeSelected: (String?) -> Unit,
    onCategorySelected: (Int?) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onPickInterests: () -> Unit,
    onOpenInbox: () -> Unit,
) {
    Scaffold(
        // The bottom navigation bar (MainScreen) already handles the bottom inset.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Eco, null, tint = MaterialTheme.colorScheme.primary)
                        Box(Modifier.width(8.dp))
                        Text(stringResource(R.string.app_name))
                    }
                },
                actions = {
                    if (state.isLoggedIn) {
                        IconButton(onClick = onOpenInbox) {
                            BadgedBox(badge = { if (state.unreadMessages > 0) Badge { Text(state.unreadMessages.toString()) } }) {
                                Icon(Icons.AutoMirrored.Filled.Chat, stringResource(R.string.messages_title))
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.isLoggedIn) {
                PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                    Tab(
                        selected = state.tab == FeedTab.FOR_YOU,
                        onClick = { onTabSelected(FeedTab.FOR_YOU) },
                        text = { Text(stringResource(R.string.feed_for_you)) },
                    )
                    Tab(
                        selected = state.tab == FeedTab.EXPLORE,
                        onClick = { onTabSelected(FeedTab.EXPLORE) },
                        text = { Text(stringResource(R.string.feed_explore)) },
                    )
                }
            }
            if (state.tab == FeedTab.EXPLORE) {
                ExploreFilters(state, onSortSelected, onTypeSelected, onCategorySelected)
            }
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.isLoading -> LoadingBox()
                    state.error != null -> ErrorBox(state.error, onRetry)
                    state.posts.isEmpty() -> EmptyBox(stringResource(if (state.tab == FeedTab.FOR_YOU) R.string.feed_for_you_empty else R.string.feed_empty))
                    else -> PostList(state, onLoadMore, onOpenPost, onOpenProfile, onPickInterests)
                }
            }
        }
    }
}

@Composable
private fun PostList(
    state: FeedUiState,
    onLoadMore: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onPickInterests: () -> Unit,
) {
    val listState = rememberLazyListState()
    LoadMoreWhenNearEnd(listState, onLoadMore)
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        state.savedAt?.let { savedAt -> item(key = "saved") { SavedContentNote(savedAt) } }
        if (state.tab == FeedTab.FOR_YOU && state.personalized == false) {
            item(key = "not-personalized") { NotPersonalizedHint(onPickInterests) }
        }
        items(state.posts, key = { it.id }) { post ->
            PostCard(post, onClick = { onOpenPost(post.id) }, onAuthorClick = onOpenProfile)
        }
        item(key = "footer") {
            PagedListFooter(state.isLoadingMore, state.loadMoreError, state.endReached, onRetry = onLoadMore)
        }
    }
}

@Composable
private fun NotPersonalizedHint(onPickInterests: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, null)
                Box(Modifier.width(12.dp))
                Text(stringResource(R.string.feed_not_personalized), style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onPickInterests, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.interests_pick))
            }
        }
    }
}

private val sortLabels: Map<PostSort, Int> = mapOf(
    PostSort.RECENT to R.string.sort_recent,
    PostSort.RATING to R.string.sort_rating,
    PostSort.VIEWS to R.string.sort_views,
    PostSort.INTERACTIONS to R.string.sort_interactions,
)

private val contentTypes = listOf(ContentTypes.PHOTO, ContentTypes.VIDEO, ContentTypes.AUDIO, ContentTypes.TEXT)

/** Sort menu + content-type chips, then category chips (requirements 8, 17). */
@Composable
private fun ExploreFilters(
    state: FeedUiState,
    onSortSelected: (PostSort) -> Unit,
    onTypeSelected: (String?) -> Unit,
    onCategorySelected: (Int?) -> Unit,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SortChip(state.sort, onSortSelected) }
            item {
                FilterChip(
                    selected = state.type == null,
                    onClick = { onTypeSelected(null) },
                    label = { Text(stringResource(R.string.filter_all_types)) },
                )
            }
            items(contentTypes) { type ->
                FilterChip(
                    selected = state.type == type,
                    onClick = { onTypeSelected(if (state.type == type) null else type) },
                    label = { Text(contentTypeLabel(type)) },
                )
            }
        }
        if (state.categories.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = state.categoryId == null,
                        onClick = { onCategorySelected(null) },
                        label = { Text(stringResource(R.string.filter_all_categories)) },
                    )
                }
                items(state.categories, key = { it.id }) { category ->
                    FilterChip(
                        selected = state.categoryId == category.id,
                        onClick = { onCategorySelected(if (state.categoryId == category.id) null else category.id) },
                        label = { Text(category.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SortChip(sort: PostSort, onSortSelected: (PostSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = true,
            onClick = { open = true },
            label = { Text(stringResource(sortLabels.getValue(sort))) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PostSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(sortLabels.getValue(option))) },
                    onClick = {
                        open = false
                        onSortSelected(option)
                    },
                )
            }
        }
    }
}
