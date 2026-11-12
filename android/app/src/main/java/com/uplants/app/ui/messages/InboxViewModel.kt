package com.uplants.app.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uplants.app.appContainer
import com.uplants.app.data.messaging.MessagingRepository
import com.uplants.app.data.model.Conversation
import com.uplants.app.ui.common.UiText
import com.uplants.app.ui.common.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InboxUiState(
    val conversations: List<Conversation> = emptyList(),
    val isLoading: Boolean = true,
    val error: UiText? = null,
)

/** Inbox (requirements 14, 20): one row per person, latest first, with how many of their messages are unread. */
class InboxViewModel(private val messaging: MessagingRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    /** Called whenever the screen is shown (coming back from a chat changes the unread counts). */
    fun load() {
        _uiState.update { it.copy(isLoading = it.conversations.isEmpty(), error = null) }
        viewModelScope.launch {
            messaging.conversations()
                .onSuccess { list -> _uiState.update { it.copy(conversations = list, isLoading = false) } }
                .onFailure { e ->
                    _uiState.update { if (it.conversations.isEmpty()) it.copy(isLoading = false, error = e.toUiText()) else it.copy(isLoading = false) }
                }
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { InboxViewModel(appContainer.messagingRepository) } }
    }
}
