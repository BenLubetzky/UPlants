package com.uplants.app.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uplants.app.R
import com.uplants.app.appContainer
import com.uplants.app.data.favorites.FavoritesRepository
import com.uplants.app.data.model.SavedPost
import com.uplants.app.data.model.UserSummary
import com.uplants.app.ui.common.PagedState
import com.uplants.app.ui.common.Paginator
import com.uplants.app.ui.common.UiText
import com.uplants.app.ui.common.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The list itself (name, owner, visibility); its posts are in [ListDetailViewModel.posts]. */
data class ListHeader(
    val name: String,
    val owner: UserSummary?,
    val isPublic: Boolean,
    val itemCount: Int,
)

data class ListDetailUiState(
    val currentUserId: String?,
    val header: ListHeader? = null,
    val isBusy: Boolean = false,
    /** Set when the list was deleted: the screen goes back. */
    val deleted: Boolean = false,
    val message: UiText? = null,
) {
    val isOwner: Boolean get() = currentUserId != null && header?.owner?.id == currentUserId
}

/** One favourite list (requirement 13). The owner can rename it, make it public/private, delete it, remove posts. */
class ListDetailViewModel(
    private val listId: String,
    currentUserId: String?,
    private val favorites: FavoritesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ListDetailUiState(currentUserId = currentUserId))
    val uiState: StateFlow<ListDetailUiState> = _uiState.asStateFlow()

    /** Each page also carries the list's details, which keep the header up to date. */
    private val pager = Paginator<SavedPost>(viewModelScope, key = { it.post.id }) { offset, limit ->
        favorites.list(listId, limit, offset)
            .onSuccess { d -> _uiState.update { it.copy(header = ListHeader(d.name, d.owner, d.isPublic, d.itemCount)) } }
            .map { it.posts }
    }
    val posts: StateFlow<PagedState<SavedPost>> = pager.state

    init {
        pager.reload()
    }

    fun reload() = pager.reload()
    fun loadMore() = pager.loadMore()

    fun rename(name: String) = update(name = name)
    fun setPublic(isPublic: Boolean) = update(isPublic = isPublic)

    private fun update(name: String? = null, isPublic: Boolean? = null) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            favorites.updateList(listId, name, isPublic)
                .onSuccess { list ->
                    _uiState.update {
                        it.copy(isBusy = false, header = it.header?.copy(name = list.name, isPublic = list.isPublic))
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isBusy = false, message = e.toUiText()) } }
        }
    }

    fun delete() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            favorites.deleteList(listId)
                .onSuccess { _uiState.update { it.copy(isBusy = false, deleted = true) } }
                .onFailure { e -> _uiState.update { it.copy(isBusy = false, message = e.toUiText()) } }
        }
    }

    fun removePost(postId: String) {
        viewModelScope.launch {
            favorites.setSaved(listId, postId, saved = false)
                .onSuccess {
                    pager.updateItems { items -> items.filterNot { it.post.id == postId } }
                    _uiState.update {
                        it.copy(
                            header = it.header?.let { h -> h.copy(itemCount = (h.itemCount - 1).coerceAtLeast(0)) },
                            message = UiText.Res(R.string.list_post_removed),
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(message = e.toUiText()) } }
        }
    }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    companion object {
        fun factory(listId: String) = viewModelFactory {
            initializer {
                val container = appContainer
                ListDetailViewModel(listId, container.sessions.current()?.userId, container.favoritesRepository)
            }
        }
    }
}
