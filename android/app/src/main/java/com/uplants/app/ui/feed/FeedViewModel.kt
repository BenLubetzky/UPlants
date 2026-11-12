package com.uplants.app.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uplants.app.appContainer
import com.uplants.app.data.content.ContentRepository
import com.uplants.app.data.content.PostFilter
import com.uplants.app.data.content.PostSort
import com.uplants.app.data.messaging.MessagingRepository
import com.uplants.app.data.model.Category
import com.uplants.app.data.model.Post
import com.uplants.app.data.model.PostPage
import com.uplants.app.ui.common.UiText
import com.uplants.app.ui.common.toUiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FeedTab { FOR_YOU, EXPLORE }

data class FeedUiState(
    val isLoggedIn: Boolean,
    val tab: FeedTab,
    // Explore filters. "For you" is ranked by the service and ignores them.
    val sort: PostSort,
    val type: String? = null,
    val categoryId: Int? = null,
    val categories: List<Category> = emptyList(),
    val posts: List<Post> = emptyList(),
    /** First page loading (nothing to show yet). */
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    /** First page failed: shown full screen. */
    val error: UiText? = null,
    /** A later page failed: shown at the bottom of the list. */
    val loadMoreError: UiText? = null,
    /** "For you" only: false = the user has no follows/interests yet, so these are popular posts. */
    val personalized: Boolean? = null,
    /** Unread messages, for the badge on the inbox button. */
    val unreadMessages: Int = 0,
    /** When the shown posts were saved, if they come from the offline cache (requirement 22). */
    val savedAt: Long? = null,
)

/**
 * Feed (requirements 2, 6–9, 16, 17). Registered users start on "For you" (personalised by
 * content-service); visitors only get "Explore", ranked by popularity, with type/category filters and sorting.
 */
class FeedViewModel(
    private val content: ContentRepository,
    private val messaging: MessagingRepository,
    isLoggedIn: Boolean,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        FeedUiState(
            isLoggedIn = isLoggedIn,
            tab = if (isLoggedIn) FeedTab.FOR_YOU else FeedTab.EXPLORE,
            // Visitors see the most popular content first (requirement 7).
            sort = if (isLoggedIn) PostSort.RECENT else PostSort.INTERACTIONS,
        )
    )
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            content.categories().onSuccess { categories -> _uiState.update { it.copy(categories = categories) } }
        }
        reload()
    }

    fun selectTab(tab: FeedTab) = changeFilter { it.copy(tab = tab) }
    fun selectSort(sort: PostSort) = changeFilter { it.copy(sort = sort) }
    fun selectType(type: String?) = changeFilter { it.copy(type = type) }
    fun selectCategory(categoryId: Int?) = changeFilter { it.copy(categoryId = categoryId) }

    fun refresh() {
        reload(refreshing = true)
        refreshUnread()
    }

    /** Called whenever the feed is shown, e.g. back from a chat. */
    fun refreshUnread() {
        if (!_uiState.value.isLoggedIn) return
        viewModelScope.launch {
            messaging.unreadCount().onSuccess { n -> _uiState.update { it.copy(unreadMessages = n) } }
        }
    }

    fun retry() = reload()

    /** Called when the list is scrolled near the end. */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isRefreshing || state.isLoadingMore || state.endReached || state.error != null) return
        _uiState.update { it.copy(isLoadingMore = true, loadMoreError = null) }
        loadJob = viewModelScope.launch {
            fetch(_uiState.value, offset = state.posts.size)
                .onSuccess { page ->
                    _uiState.update { s ->
                        val known = s.posts.mapTo(HashSet()) { it.id }
                        s.copy(
                            posts = s.posts + page.posts.filter { it.id !in known },
                            isLoadingMore = false,
                            endReached = page.posts.size < PAGE_SIZE,
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoadingMore = false, loadMoreError = e.toUiText()) } }
        }
    }

    /** Back from a post's detail screen: its likes, comments, rating or views may have changed. */
    fun refreshPost(postId: String) {
        if (_uiState.value.posts.none { it.id == postId }) return
        viewModelScope.launch {
            content.post(postId).onSuccess { fresh ->
                _uiState.update { s -> s.copy(posts = s.posts.map { if (it.id == postId) fresh else it }) }
            }
        }
    }

    /** A post was deleted from its detail screen. */
    fun removePost(postId: String) = _uiState.update { s -> s.copy(posts = s.posts.filterNot { it.id == postId }) }

    private fun changeFilter(change: (FeedUiState) -> FeedUiState) {
        val before = _uiState.value
        val after = change(before)
        if (after == before) return
        _uiState.value = after
        reload()
    }

    private fun reload(refreshing: Boolean = false) {
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                posts = if (refreshing) it.posts else emptyList(),
                isLoading = !refreshing,
                isRefreshing = refreshing,
                isLoadingMore = false,
                endReached = false,
                error = null,
                loadMoreError = null,
            )
        }
        loadJob = viewModelScope.launch {
            fetch(_uiState.value, offset = 0)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            posts = page.posts,
                            isLoading = false,
                            isRefreshing = false,
                            endReached = page.posts.size < PAGE_SIZE,
                            personalized = page.personalized,
                            savedAt = page.savedAt,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        // When refreshing a list that is already showing, keep it and don't go full-screen error.
                        if (refreshing && it.posts.isNotEmpty()) it.copy(isRefreshing = false, loadMoreError = e.toUiText())
                        else it.copy(isLoading = false, isRefreshing = false, error = e.toUiText(), posts = emptyList())
                    }
                }
        }
    }

    private suspend fun fetch(state: FeedUiState, offset: Int): Result<PostPage> = when (state.tab) {
        FeedTab.FOR_YOU -> content.feed(PAGE_SIZE, offset)
        FeedTab.EXPLORE -> content.posts(
            PostFilter(type = state.type, categoryId = state.categoryId, sort = state.sort),
            PAGE_SIZE,
            offset,
        )
    }

    companion object {
        const val PAGE_SIZE = 20

        fun factory(isLoggedIn: Boolean) = viewModelFactory {
            initializer { FeedViewModel(appContainer.contentRepository, appContainer.messagingRepository, isLoggedIn) }
        }
    }
}
