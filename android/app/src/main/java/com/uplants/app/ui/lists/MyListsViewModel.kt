package com.uplants.app.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uplants.app.appContainer
import com.uplants.app.data.favorites.FavoritesRepository
import com.uplants.app.data.model.FavoriteList
import com.uplants.app.ui.common.UiText
import com.uplants.app.ui.common.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MyListsUiState(
    val lists: List<FavoriteList> = emptyList(),
    val isLoading: Boolean = true,
    val error: UiText? = null,
    val isCreating: Boolean = false,
    /** Set when a list was created: the dialog closes. */
    val created: Boolean = false,
    val message: UiText? = null,
)

/** My favourite lists (requirement 13): see them all, create a new one. */
class MyListsViewModel(private val favorites: FavoritesRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(MyListsUiState())
    val uiState: StateFlow<MyListsUiState> = _uiState.asStateFlow()

    /** Called whenever the screen is shown: posts may have been saved from a post's "Save to…" sheet meanwhile. */
    fun load() {
        _uiState.update { it.copy(isLoading = it.lists.isEmpty(), error = null) }
        viewModelScope.launch {
            favorites.myLists()
                .onSuccess { lists -> _uiState.update { it.copy(lists = lists, isLoading = false) } }
                .onFailure { e ->
                    _uiState.update {
                        if (it.lists.isEmpty()) it.copy(isLoading = false, error = e.toUiText())
                        else it.copy(isLoading = false, message = e.toUiText())
                    }
                }
        }
    }

    fun create(name: String, isPublic: Boolean) {
        if (name.isBlank() || _uiState.value.isCreating) return
        _uiState.update { it.copy(isCreating = true) }
        viewModelScope.launch {
            favorites.createList(name, isPublic)
                .onSuccess { list -> _uiState.update { it.copy(lists = listOf(list) + it.lists, isCreating = false, created = true) } }
                .onFailure { e -> _uiState.update { it.copy(isCreating = false, message = e.toUiText()) } }
        }
    }

    fun createdHandled() = _uiState.update { it.copy(created = false) }
    fun messageShown() = _uiState.update { it.copy(message = null) }

    companion object {
        val Factory = viewModelFactory { initializer { MyListsViewModel(appContainer.favoritesRepository) } }
    }
}
