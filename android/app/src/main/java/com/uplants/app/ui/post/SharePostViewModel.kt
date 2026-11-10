package com.uplants.app.ui.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uplants.app.appContainer
import com.uplants.app.data.messaging.MessagingRepository
import com.uplants.app.data.model.UserSummary
import com.uplants.app.data.users.UsersRepository
import com.uplants.app.ui.common.UiText
import com.uplants.app.ui.common.toUiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SharePostUiState(
    val query: String = "",
    val results: List<UserSummary> = emptyList(),
    val isSearching: Boolean = false,
    val error: UiText? = null,
    /** Optional text sent with the post. */
    val note: String = "",
    /** User the post is being sent to right now. */
    val sendingToId: String? = null,
    /** Set once sent: the sheet closes. */
    val sentTo: UserSummary? = null,
)

/** Share sheet (requirement 14): find a registered user and send them the post through messaging-service. */
class SharePostViewModel(
    private val postId: String,
    private val currentUserId: String?,
    private val users: UsersRepository,
    private val messaging: MessagingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SharePostUiState())
    val uiState: StateFlow<SharePostUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query.take(50), error = null) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(results = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS) // wait until the user stops typing
            _uiState.update { it.copy(isSearching = true) }
            users.search(query)
                .onSuccess { page ->
                    _uiState.update { s -> s.copy(results = page.users.filter { it.id != currentUserId }, isSearching = false) }
                }
                .onFailure { e -> _uiState.update { it.copy(isSearching = false, error = e.toUiText()) } }
        }
    }

    fun onNoteChange(note: String) = _uiState.update { it.copy(note = note.take(2000)) }

    fun shareWith(user: UserSummary) {
        if (_uiState.value.sendingToId != null) return
        _uiState.update { it.copy(sendingToId = user.id, error = null) }
        viewModelScope.launch {
            messaging.sharePost(user.id, postId, _uiState.value.note)
                .onSuccess { _uiState.update { it.copy(sendingToId = null, sentTo = user) } }
                .onFailure { e -> _uiState.update { it.copy(sendingToId = null, error = e.toUiText()) } }
        }
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 350L

        fun factory(postId: String) = viewModelFactory {
            initializer {
                val container = appContainer
                SharePostViewModel(
                    postId,
                    container.sessions.current()?.userId,
                    container.usersRepository,
                    container.messagingRepository,
                )
            }
        }
    }
}
