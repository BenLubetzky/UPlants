package com.uplants.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uplants.app.appContainer
import com.uplants.app.data.content.ContentRepository
import com.uplants.app.data.content.PostFilter
import com.uplants.app.data.content.PostSort
import com.uplants.app.data.model.Post
import com.uplants.app.data.model.UserSummary
import com.uplants.app.data.users.UsersRepository
import com.uplants.app.ui.common.PagedState
import com.uplants.app.ui.common.Paginator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SearchTab { POSTS, PEOPLE }

/**
 * Search (requirements 2 and 3): posts by full-text search in content-service (title, description,
 * plant species), and people by username / display name in users-service. Visitors can search too.
 */
class SearchViewModel(
    private val content: ContentRepository,
    private val users: UsersRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _tab = MutableStateFlow(SearchTab.POSTS)
    val tab: StateFlow<SearchTab> = _tab.asStateFlow()

    private val postsPager = Paginator<Post>(viewModelScope, key = { it.id }) { offset, limit ->
        content.posts(PostFilter(query = _query.value.trim(), sort = PostSort.INTERACTIONS), limit, offset).map { it.posts }
    }
    private val peoplePager = Paginator<UserSummary>(viewModelScope, key = { it.id }) { offset, limit ->
        users.search(_query.value.trim(), limit, offset).map { it.users }
    }
    val posts: StateFlow<PagedState<Post>> = postsPager.state
    val people: StateFlow<PagedState<UserSummary>> = peoplePager.state

    private var searchJob: Job? = null

    fun onQueryChange(text: String) {
        _query.value = text.take(MAX_QUERY)
        searchJob?.cancel()
        if (text.isBlank()) {
            postsPager.clear()
            peoplePager.clear()
            return
        }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS) // wait until the user stops typing
            search()
        }
    }

    /** Keyboard "search" button: no need to wait for the debounce. */
    fun searchNow() {
        searchJob?.cancel()
        if (_query.value.isNotBlank()) search()
    }

    fun selectTab(tab: SearchTab) {
        _tab.value = tab
    }

    fun loadMorePosts() = postsPager.loadMore()
    fun loadMorePeople() = peoplePager.loadMore()
    fun retry() = search()

    /** Both lists are searched at once, so switching tabs shows results immediately. */
    private fun search() {
        postsPager.reload()
        peoplePager.reload()
    }

    /** Back from a post's detail screen. */
    fun refreshPost(postId: String) {
        if (posts.value.items.none { it.id == postId }) return
        viewModelScope.launch {
            content.post(postId).onSuccess { fresh -> postsPager.updateItems { list -> list.map { if (it.id == postId) fresh else it } } }
        }
    }

    fun removePost(postId: String) = postsPager.updateItems { list -> list.filterNot { it.id == postId } }

    companion object {
        private const val DEBOUNCE_MS = 400L
        private const val MAX_QUERY = 50

        val Factory = viewModelFactory {
            initializer { SearchViewModel(appContainer.contentRepository, appContainer.usersRepository) }
        }
    }
}
