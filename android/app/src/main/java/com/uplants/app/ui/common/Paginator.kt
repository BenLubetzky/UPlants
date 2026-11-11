package com.uplants.app.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A list loaded page by page from a service (limit/offset). */
data class PagedState<T>(
    val items: List<T> = emptyList(),
    /** First page loading: nothing to show yet. */
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    /** First page failed. */
    val error: UiText? = null,
    /** A later page failed: shown under the list. */
    val loadMoreError: UiText? = null,
) {
    val isEmpty: Boolean get() = items.isEmpty() && !isLoading && error == null
}

/**
 * Loads pages through [fetch] (offset, limit) and keeps them in [state]. Used by ViewModels that show a
 * scrolling list (profile posts, search results, followers). Duplicates across pages are dropped by [key].
 */
class Paginator<T>(
    private val scope: CoroutineScope,
    private val pageSize: Int = 20,
    private val key: (T) -> Any,
    private val fetch: suspend (offset: Int, limit: Int) -> Result<List<T>>,
) {
    private val _state = MutableStateFlow(PagedState<T>())
    val state: StateFlow<PagedState<T>> = _state.asStateFlow()

    private var job: Job? = null

    /** Starts over from the first page (e.g. a new search query). */
    fun reload() {
        job?.cancel()
        _state.value = PagedState(isLoading = true)
        job = scope.launch {
            fetch(0, pageSize)
                .onSuccess { page -> _state.value = PagedState(items = page, endReached = page.size < pageSize) }
                .onFailure { e -> _state.value = PagedState(error = e.toUiText()) }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoading || s.isLoadingMore || s.endReached || s.error != null) return
        _state.update { it.copy(isLoadingMore = true, loadMoreError = null) }
        job = scope.launch {
            fetch(s.items.size, pageSize)
                .onSuccess { page ->
                    _state.update { cur ->
                        val known = cur.items.mapTo(HashSet(), key)
                        cur.copy(
                            items = cur.items + page.filter { key(it) !in known },
                            isLoadingMore = false,
                            endReached = page.size < pageSize,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(isLoadingMore = false, loadMoreError = e.toUiText()) } }
        }
    }

    /** Empties the list without loading (e.g. the search box was cleared). */
    fun clear() {
        job?.cancel()
        _state.value = PagedState()
    }

    /** Changes items in place (an updated post, a removed one, ...). */
    fun updateItems(transform: (List<T>) -> List<T>) = _state.update { it.copy(items = transform(it.items)) }
}
