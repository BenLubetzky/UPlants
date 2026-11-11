package com.uplants.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uplants.app.R
import com.uplants.app.appContainer
import com.uplants.app.data.content.ContentRepository
import com.uplants.app.data.model.Category
import com.uplants.app.data.users.UsersRepository
import com.uplants.app.ui.common.UiText
import com.uplants.app.ui.common.toUiText
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InterestsUiState(
    val categories: List<Category> = emptyList(),
    val selected: Set<Int> = emptySet(),
    val isLoading: Boolean = true,
    val error: UiText? = null,
    val isSaving: Boolean = false,
    /** Set once saved: the screen closes. */
    val saved: Boolean = false,
    val message: UiText? = null,
)

/** Pick interest categories (requirement 9): they shape the "For you" feed. */
class InterestsViewModel(
    private val users: UsersRepository,
    private val content: ContentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InterestsUiState())
    val uiState: StateFlow<InterestsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val categories = async { content.categories() }
            val mine = async { users.interests() }
            val c = categories.await()
            val m = mine.await()
            val failure = c.exceptionOrNull() ?: m.exceptionOrNull()
            _uiState.update {
                if (failure != null) it.copy(isLoading = false, error = failure.toUiText())
                else it.copy(
                    isLoading = false,
                    categories = c.getOrThrow(),
                    selected = m.getOrThrow().mapTo(HashSet()) { cat -> cat.id },
                )
            }
        }
    }

    fun toggle(categoryId: Int) = _uiState.update {
        it.copy(selected = if (categoryId in it.selected) it.selected - categoryId else it.selected + categoryId)
    }

    fun save() {
        if (_uiState.value.isSaving) return
        if (_uiState.value.selected.size > MAX_INTERESTS) {
            _uiState.update { it.copy(message = UiText.Res(R.string.interests_too_many)) }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            users.setInterests(_uiState.value.selected.sorted())
                .onSuccess { _uiState.update { it.copy(isSaving = false, saved = true) } }
                .onFailure { e -> _uiState.update { it.copy(isSaving = false, message = e.toUiText()) } }
        }
    }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    companion object {
        const val MAX_INTERESTS = 20

        val Factory = viewModelFactory {
            initializer { InterestsViewModel(appContainer.usersRepository, appContainer.contentRepository) }
        }
    }
}
