package com.uplants.app.ui.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uplants.app.appContainer
import com.uplants.app.data.favorites.FavoritesRepository
import com.uplants.app.data.model.ListMembership
import com.uplants.app.ui.common.UiText
import com.uplants.app.ui.common.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SaveToListUiState(
    val lists: List<ListMembership> = emptyList(),
    val isLoading: Boolean = true,
    val error: UiText? = null,
    /** Lists whose checkbox is being saved right now. */
    val busyListIds: Set<String> = emptySet(),
    val newListName: String = "",
    val isCreating: Boolean = false,
    val message: UiText? = null,
)

/** "Save to…" sheet (requirement 13): my favourite lists with a checkbox each, plus "new list". */
class SaveToListViewModel(
    private val postId: String,
    private val favorites: FavoritesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SaveToListUiState())
    val uiState: StateFlow<SaveToListUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            favorites.listsForPost(postId)
                .onSuccess { lists -> _uiState.update { it.copy(lists = lists, isLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.toUiText()) } }
        }
    }

    fun toggle(listId: String) {
        val list = _uiState.value.lists.firstOrNull { it.id == listId } ?: return
        if (listId in _uiState.value.busyListIds) return
        val saved = !list.containsPost
        _uiState.update { it.copy(lists = it.lists.withSaved(listId, saved), busyListIds = it.busyListIds + listId) }
        viewModelScope.launch {
            val result = favorites.setSaved(listId, postId, saved)
            _uiState.update {
                it.copy(
                    lists = if (result.isFailure) it.lists.withSaved(listId, !saved) else it.lists,
                    busyListIds = it.busyListIds - listId,
                    message = result.exceptionOrNull()?.toUiText(),
                )
            }
        }
    }

    fun onNewListNameChange(name: String) = _uiState.update { it.copy(newListName = name.take(60)) }

    /** Creates the list and saves the post in it straight away. */
    fun createList() {
        val name = _uiState.value.newListName.trim()
        if (name.isEmpty() || _uiState.value.isCreating) return
        _uiState.update { it.copy(isCreating = true) }
        viewModelScope.launch {
            favorites.createList(name)
                .onSuccess { list ->
                    val saved = favorites.setSaved(list.id, postId, true).isSuccess
                    _uiState.update {
                        it.copy(
                            isCreating = false,
                            newListName = "",
                            lists = listOf(ListMembership(list.id, list.name, list.isPublic, saved)) + it.lists,
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isCreating = false, message = e.toUiText()) } }
        }
    }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    private fun List<ListMembership>.withSaved(listId: String, saved: Boolean) =
        map { if (it.id == listId) it.copy(containsPost = saved) else it }

    companion object {
        fun factory(postId: String) = viewModelFactory {
            initializer { SaveToListViewModel(postId, appContainer.favoritesRepository) }
        }
    }
}
