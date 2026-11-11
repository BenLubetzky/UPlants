package com.uplants.app.ui.profile

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uplants.app.R
import com.uplants.app.appContainer
import com.uplants.app.data.model.UserSummary
import com.uplants.app.data.users.UsersRepository
import com.uplants.app.ui.common.EmptyBox
import com.uplants.app.ui.common.ErrorBox
import com.uplants.app.ui.common.LoadMoreWhenNearEnd
import com.uplants.app.ui.common.LoadingBox
import com.uplants.app.ui.common.PagedListFooter
import com.uplants.app.ui.common.PagedState
import com.uplants.app.ui.common.Paginator
import com.uplants.app.ui.common.UserRow
import kotlinx.coroutines.flow.StateFlow

/** Followers or following of a user (requirement 20). */
class FollowListViewModel(userId: String, followers: Boolean, users: UsersRepository) : ViewModel() {
    private val pager = Paginator<UserSummary>(viewModelScope, key = { it.id }) { offset, limit ->
        (if (followers) users.followers(userId, limit, offset) else users.following(userId, limit, offset)).map { it.users }
    }
    val users: StateFlow<PagedState<UserSummary>> = pager.state

    init {
        pager.reload()
    }

    fun retry() = pager.reload()
    fun loadMore() = pager.loadMore()

    companion object {
        fun factory(userId: String, followers: Boolean) = viewModelFactory {
            initializer { FollowListViewModel(userId, followers, appContainer.usersRepository) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowListScreen(
    userId: String,
    followers: Boolean,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    viewModel: FollowListViewModel = viewModel(key = "follow-$userId-$followers", factory = FollowListViewModel.factory(userId, followers)),
) {
    val state by viewModel.users.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (followers) R.string.profile_stat_followers else R.string.profile_stat_following)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            state.isLoading -> LoadingBox(modifier)
            state.error != null -> ErrorBox(state.error!!, viewModel::retry, modifier)
            state.items.isEmpty() -> EmptyBox(stringResource(if (followers) R.string.followers_empty else R.string.following_empty), modifier)
            else -> {
                val listState = rememberLazyListState()
                LoadMoreWhenNearEnd(listState, viewModel::loadMore)
                LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
                    items(state.items, key = { it.id }) { user -> UserRow(user, onClick = { onOpenProfile(user.id) }) }
                    item { PagedListFooter(state.isLoadingMore, state.loadMoreError, state.endReached, viewModel::loadMore, endText = null) }
                }
            }
        }
    }
}
