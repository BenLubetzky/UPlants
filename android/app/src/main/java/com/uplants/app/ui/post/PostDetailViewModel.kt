package com.uplants.app.ui.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.uplants.app.R
import com.uplants.app.appContainer
import com.uplants.app.data.content.ContentRepository
import com.uplants.app.data.interactions.InteractionsRepository
import com.uplants.app.data.model.Comment
import com.uplants.app.data.model.Post
import com.uplants.app.ui.common.UiText
import com.uplants.app.ui.common.toUiText
import com.uplants.app.ui.navigation.PostDetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PostDetailUiState(
    /** Null for visitors. */
    val currentUserId: String?,
    val post: Post? = null,
    val isLoading: Boolean = true,
    val error: UiText? = null,
    /** 1 like, -1 dislike, null none. */
    val myReaction: Int? = null,
    /** 0-5, null not rated. */
    val myStars: Int? = null,
    val comments: List<Comment> = emptyList(),
    val commentsLoading: Boolean = false,
    val commentsEndReached: Boolean = false,
    val commentsError: UiText? = null,
    val commentDraft: String = "",
    val isSendingComment: Boolean = false,
    val isDeleting: Boolean = false,
    /** Set once the post was deleted: the screen goes back. */
    val deleted: Boolean = false,
    /** One-off message for a snackbar. */
    val message: UiText? = null,
) {
    val isLoggedIn: Boolean get() = currentUserId != null
    val isMine: Boolean get() = currentUserId != null && post?.author?.id == currentUserId
}

/** Post detail (requirements 2, 11, 12, 16): the post, its comments, like/dislike, 0–5 stars, view counting. */
class PostDetailViewModel(
    private val postId: String,
    currentUserId: String?,
    private val content: ContentRepository,
    private val interactions: InteractionsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostDetailUiState(currentUserId = currentUserId))
    val uiState: StateFlow<PostDetailUiState> = _uiState.asStateFlow()

    private var viewRecorded = false

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            content.post(postId)
                .onSuccess { post ->
                    _uiState.update { it.copy(post = post, isLoading = false) }
                    recordViewOnce()
                    loadMine()
                    loadComments(reset = true)
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.toUiText()) } }
        }
    }

    /** Counts a view once per screen; the service ignores repeats within 30 minutes anyway. */
    private fun recordViewOnce() {
        if (viewRecorded) return
        viewRecorded = true
        viewModelScope.launch { interactions.recordView(postId).onSuccess { refreshPostCounters() } }
    }

    private fun loadMine() {
        if (!_uiState.value.isLoggedIn) return
        viewModelScope.launch {
            interactions.mine(postId).onSuccess { mine ->
                _uiState.update { it.copy(myReaction = mine.reaction, myStars = mine.stars) }
            }
        }
    }

    fun loadMoreComments() = loadComments(reset = false)

    private fun loadComments(reset: Boolean) {
        val state = _uiState.value
        if (state.commentsLoading || (!reset && state.commentsEndReached)) return
        val offset = if (reset) 0 else state.comments.size
        _uiState.update { it.copy(commentsLoading = true, commentsError = null) }
        viewModelScope.launch {
            interactions.comments(postId, COMMENTS_PAGE, offset)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            comments = if (reset) page.comments else it.comments + page.comments,
                            commentsLoading = false,
                            commentsEndReached = page.comments.size < COMMENTS_PAGE,
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(commentsLoading = false, commentsError = e.toUiText()) } }
        }
    }

    /** Tapping the active button again removes the reaction. Updates the counters right away, then syncs. */
    fun react(value: Int) {
        val before = _uiState.value
        val post = before.post ?: return
        val newReaction = if (before.myReaction == value) null else value
        _uiState.update { it.copy(myReaction = newReaction, post = post.withReaction(before.myReaction, newReaction)) }
        viewModelScope.launch {
            interactions.setReaction(postId, newReaction)
                .onSuccess { refreshPostCounters() }
                .onFailure { e -> _uiState.update { it.copy(myReaction = before.myReaction, post = post, message = e.toUiText()) } }
        }
    }

    /** [stars] null removes the rating. The new average comes from the service. */
    fun rate(stars: Int?) {
        val before = _uiState.value.myStars
        _uiState.update { it.copy(myStars = stars) }
        viewModelScope.launch {
            interactions.setRating(postId, stars)
                .onSuccess { refreshPostCounters() }
                .onFailure { e -> _uiState.update { it.copy(myStars = before, message = e.toUiText()) } }
        }
    }

    fun onCommentDraftChange(text: String) = _uiState.update { it.copy(commentDraft = text.take(COMMENT_MAX_LENGTH)) }

    fun sendComment() {
        val state = _uiState.value
        val body = state.commentDraft.trim()
        if (body.isEmpty() || state.isSendingComment) return
        _uiState.update { it.copy(isSendingComment = true) }
        viewModelScope.launch {
            interactions.addComment(postId, body)
                .onSuccess { comment ->
                    _uiState.update {
                        it.copy(
                            isSendingComment = false,
                            commentDraft = "",
                            // Comments are oldest first: the new one belongs at the end, if the end is loaded.
                            comments = if (it.commentsEndReached) it.comments + comment else it.comments,
                            post = it.post?.let { p -> p.copy(commentCount = p.commentCount + 1) },
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isSendingComment = false, message = e.toUiText()) } }
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            interactions.deleteComment(commentId)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            comments = it.comments.filterNot { c -> c.id == commentId },
                            post = it.post?.let { p -> p.copy(commentCount = (p.commentCount - 1).coerceAtLeast(0)) },
                            message = UiText.Res(R.string.comment_deleted),
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(message = e.toUiText()) } }
        }
    }

    fun deletePost() {
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            content.deletePost(postId)
                .onSuccess { _uiState.update { it.copy(isDeleting = false, deleted = true) } }
                .onFailure { e -> _uiState.update { it.copy(isDeleting = false, message = e.toUiText()) } }
        }
    }

    /** Messages from the share / save sheets, shown in this screen's snackbar. */
    fun showMessage(message: UiText) = _uiState.update { it.copy(message = message) }

    /** After sharing, the database raised the share counter. */
    fun onShared(message: UiText) {
        showMessage(message)
        viewModelScope.launch { refreshPostCounters() }
    }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    /** After a like or rating, the database updates the counters; fetch them quietly. */
    private suspend fun refreshPostCounters() {
        content.post(postId).onSuccess { fresh -> _uiState.update { it.copy(post = fresh) } }
    }

    companion object {
        private const val COMMENTS_PAGE = 20
        const val COMMENT_MAX_LENGTH = 1000

        val Factory = viewModelFactory {
            initializer {
                val route = createSavedStateHandle().toRoute<PostDetailRoute>()
                val container = appContainer
                PostDetailViewModel(
                    postId = route.postId,
                    currentUserId = container.sessions.current()?.userId,
                    content = container.contentRepository,
                    interactions = container.interactionsRepository,
                )
            }
        }
    }
}

/** Adjusts like/dislike counters locally when the user's reaction changes from [old] to [new]. */
internal fun Post.withReaction(old: Int?, new: Int?): Post {
    var likes = likeCount
    var dislikes = dislikeCount
    when (old) { 1 -> likes--; -1 -> dislikes-- }
    when (new) { 1 -> likes++; -1 -> dislikes++ }
    return copy(likeCount = likes.coerceAtLeast(0), dislikeCount = dislikes.coerceAtLeast(0))
}
