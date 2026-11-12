package com.uplants.app.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uplants.app.appContainer
import com.uplants.app.data.messaging.MessagingRepository
import com.uplants.app.data.model.Message
import com.uplants.app.ui.common.UiText
import com.uplants.app.ui.common.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

data class ChatUiState(
    val currentUserId: String,
    /** Oldest first, as drawn on screen. */
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val error: UiText? = null,
    val isLoadingOlder: Boolean = false,
    val olderEndReached: Boolean = false,
    val draft: String = "",
    val isSending: Boolean = false,
    val message: UiText? = null,
)

/**
 * A conversation with one user (requirements 14, 20): text messages and shared posts.
 * The screen polls [refreshLatest] while it is visible, so replies show up without reopening.
 */
class ChatViewModel(
    private val otherUserId: String,
    currentUserId: String,
    private val messaging: MessagingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState(currentUserId = currentUserId))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            messaging.thread(otherUserId, PAGE, 0)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(messages = page.messages.reversed(), isLoading = false, olderEndReached = page.messages.size < PAGE)
                    }
                    markReadIfNeeded()
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.toUiText()) } }
        }
    }

    /** Fetches the newest page and adds anything not on screen yet. */
    fun refreshLatest() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            messaging.thread(otherUserId, PAGE, 0).onSuccess { page -> merge(page.messages) }
        }
    }

    fun loadOlder() {
        val s = _uiState.value
        if (s.isLoading || s.isLoadingOlder || s.olderEndReached) return
        _uiState.update { it.copy(isLoadingOlder = true) }
        viewModelScope.launch {
            messaging.thread(otherUserId, PAGE, s.messages.size)
                .onSuccess { page ->
                    _uiState.update { cur ->
                        val known = cur.messages.mapTo(HashSet()) { it.id }
                        cur.copy(
                            messages = page.messages.reversed().filter { it.id !in known } + cur.messages,
                            isLoadingOlder = false,
                            olderEndReached = page.messages.size < PAGE,
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoadingOlder = false, message = e.toUiText()) } }
        }
    }

    fun onDraftChange(text: String) = _uiState.update { it.copy(draft = text.take(MAX_LENGTH)) }

    fun send() {
        val s = _uiState.value
        val text = s.draft.trim()
        if (text.isEmpty() || s.isSending) return
        _uiState.update { it.copy(isSending = true) }
        viewModelScope.launch {
            messaging.sendText(otherUserId, text)
                .onSuccess { sent ->
                    _uiState.update { it.copy(isSending = false, draft = "") }
                    merge(listOf(sent))
                }
                .onFailure { e -> _uiState.update { it.copy(isSending = false, message = e.toUiText()) } }
        }
    }

    /** Only your own messages; deleting a shared post lowers its share count again. */
    fun delete(messageId: String) {
        viewModelScope.launch {
            messaging.delete(messageId)
                .onSuccess { _uiState.update { s -> s.copy(messages = s.messages.filterNot { it.id == messageId }) } }
                .onFailure { e -> _uiState.update { it.copy(message = e.toUiText()) } }
        }
    }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    private suspend fun merge(newestFirst: List<Message>) {
        var added = false
        _uiState.update { cur ->
            val known = cur.messages.mapTo(HashSet()) { it.id }
            val fresh = newestFirst.filter { it.id !in known }.reversed()
            added = fresh.isNotEmpty()
            if (!added) cur else cur.copy(messages = (cur.messages + fresh).sortedBy { it.createdAt })
        }
        if (added) markReadIfNeeded()
    }

    /** Marks their messages as read, so the inbox badge goes away. */
    private suspend fun markReadIfNeeded() {
        val me = _uiState.value.currentUserId
        if (_uiState.value.messages.none { it.senderId != me && it.readAt == null }) return
        messaging.markRead(otherUserId).onSuccess {
            val now = OffsetDateTime.now().toString()
            _uiState.update { s ->
                s.copy(messages = s.messages.map { if (it.senderId != me && it.readAt == null) it.copy(readAt = now) else it })
            }
        }
    }

    companion object {
        private const val PAGE = 30
        const val MAX_LENGTH = 2000

        fun factory(otherUserId: String) = viewModelFactory {
            initializer {
                val container = appContainer
                ChatViewModel(otherUserId, container.sessions.current()?.userId.orEmpty(), container.messagingRepository)
            }
        }
    }
}
