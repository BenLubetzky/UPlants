package com.uplants.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uplants.app.R
import com.uplants.app.data.model.Post
import com.uplants.app.data.model.UserSummary
import com.uplants.app.ui.common.EmptyBox
import com.uplants.app.ui.common.ErrorBox
import com.uplants.app.ui.common.LoadMoreWhenNearEnd
import com.uplants.app.ui.common.LoadingBox
import com.uplants.app.ui.common.PagedListFooter
import com.uplants.app.ui.common.PagedState
import com.uplants.app.ui.common.PostCard
import com.uplants.app.ui.common.UserRow

@Composable
fun SearchScreen(
    onOpenPost: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    viewedPostId: String?,
    deletedPostId: String?,
    onPostChangesHandled: () -> Unit,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    LaunchedEffect(viewedPostId, deletedPostId) {
        deletedPostId?.let(viewModel::removePost)
        viewedPostId?.takeIf { it != deletedPostId }?.let(viewModel::refreshPost)
        if (viewedPostId != null || deletedPostId != null) onPostChangesHandled()
    }
    SearchContent(
        query = query,
        tab = tab,
        posts = posts,
        people = people,
        onQueryChange = viewModel::onQueryChange,
        onSearch = viewModel::searchNow,
        onTabSelected = viewModel::selectTab,
        onRetry = viewModel::retry,
        onLoadMorePosts = viewModel::loadMorePosts,
        onLoadMorePeople = viewModel::loadMorePeople,
        onOpenPost = onOpenPost,
        onOpenProfile = onOpenProfile,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchContent(
    query: String,
    tab: SearchTab,
    posts: PagedState<Post>,
    people: PagedState<UserSummary>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onTabSelected: (SearchTab) -> Unit,
    onRetry: () -> Unit,
    onLoadMorePosts: () -> Unit,
    onLoadMorePeople: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val focus = LocalFocusManager.current
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.search_title)) }) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Close, stringResource(R.string.search_clear)) }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(); focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                Tab(selected = tab == SearchTab.POSTS, onClick = { onTabSelected(SearchTab.POSTS) }, text = { Text(stringResource(R.string.search_tab_posts)) })
                Tab(selected = tab == SearchTab.PEOPLE, onClick = { onTabSelected(SearchTab.PEOPLE) }, text = { Text(stringResource(R.string.search_tab_people)) })
            }
            val state: PagedState<*> = if (tab == SearchTab.POSTS) posts else people
            when {
                query.isBlank() -> EmptyBox(stringResource(R.string.search_start))
                state.isLoading -> LoadingBox()
                state.error != null -> ErrorBox(state.error, onRetry)
                state.items.isEmpty() -> EmptyBox(stringResource(R.string.search_no_results, query.trim()))
                tab == SearchTab.POSTS -> {
                    val listState = rememberLazyListState()
                    LoadMoreWhenNearEnd(listState, onLoadMorePosts)
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(posts.items, key = { it.id }) { post ->
                            PostCard(post, onClick = { onOpenPost(post.id) }, onAuthorClick = onOpenProfile)
                        }
                        item { PagedListFooter(posts.isLoadingMore, posts.loadMoreError, posts.endReached, onLoadMorePosts, endText = null) }
                    }
                }
                else -> {
                    val listState = rememberLazyListState()
                    LoadMoreWhenNearEnd(listState, onLoadMorePeople)
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(people.items, key = { it.id }) { user -> UserRow(user, onClick = { onOpenProfile(user.id) }) }
                        item { PagedListFooter(people.isLoadingMore, people.loadMoreError, people.endReached, onLoadMorePeople, endText = null) }
                    }
                }
            }
        }
    }
}
