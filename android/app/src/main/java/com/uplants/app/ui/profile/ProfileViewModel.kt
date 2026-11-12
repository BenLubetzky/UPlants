package com.uplants.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uplants.app.R
import com.uplants.app.appContainer
import com.uplants.app.data.content.ContentRepository
import com.uplants.app.data.content.PostFilter
import com.uplants.app.data.content.PostSort
import com.uplants.app.data.favorites.FavoritesRepository
import com.uplants.app.data.model.FavoriteList
import com.uplants.app.data.model.Post
import com.uplants.app.data.model.Profile
import com.uplants.app.data.users.UsersRepository
import com.uplants.app.ui.common.PagedState
import com.uplants.app.ui.common.Paginator
import com.uplants.app.ui.common.UiText
import com.uplants.app.ui.common.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val userId: String,
    /** Null for visitors. */
    val currentUserId: String?,
    val profile: Profile? = null,
    /** Their public lists (all of them on your own profile). */
    val lists: List<FavoriteList> = emptyList(),
    val isLoading: Boolean = true,
    val error: UiText? = null,
    val isFollowBusy: Boolean = false,
    val isSavingProfile: Boolean = false,
    /** Set when the edit dialog saved successfully: the dialog closes. */
    val profileSaved: Boolean = false,
    val message: UiText? = null,
) {
    val isMe: Boolean get() = currentUserId != null && currentUserId == userId
    val isLoggedIn: Boolean get() = currentUserId != null
}

/**
 * A user's profile (requirements 3–5, 20): username, description, counters, their published posts,
 * and follow / unfollow. On your own profile: edit name and description.
 */
class ProfileViewModel(
    userId: String,
    currentUserId: String?,
    private val users: UsersRepository,
    private val content: ContentRepository,
    private val favorites: FavoritesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState(userId = userId, currentUserId = currentUserId))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    /** The user's published posts, newest first. */
    private val postsPager = Paginator<Post>(viewModelScope, key = { it.id }) { offset, limit ->
        content.posts(PostFilter(authorId = userId, sort = PostSort.RECENT), limit, offset).map { it.posts }
    }
    val posts: StateFlow<PagedState<Post>> = postsPager.state

    init {
        load()
    }

    fun retry() = load()

    fun refresh() {
        loadProfile(quietly = true)
        postsPager.reload()
        loadLists()
    }

    private fun load() {
        loadProfile(quietly = false)
        postsPager.reload()
        loadLists()
    }

    /** Lists are optional on a profile: if favorites-service is down the rest still shows. */
    private fun loadLists() {
        viewModelScope.launch {
            favorites.userLists(_uiState.value.userId).onSuccess { lists -> _uiState.update { it.copy(lists = lists) } }
        }
    }

    private fun loadProfile(quietly: Boolean) {
        if (!quietly) _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            users.profile(_uiState.value.userId)
                .onSuccess { p -> _uiState.update { it.copy(profile = p, isLoading = false, error = null) } }
                .onFailure { e ->
                    _uiState.update {
                        if (quietly && it.profile != null) it.copy(message = e.toUiText())
                        else it.copy(isLoading = false, error = e.toUiText())
                    }
                }
        }
    }

    fun loadMorePosts() = postsPager.loadMore()

    /** Follow / unfollow. The counters update right away, then come back from the service. */
    fun toggleFollow() {
        val s = _uiState.value
        val profile = s.profile ?: return
        if (s.isFollowBusy || s.isMe || !s.isLoggedIn) return
        val follow = profile.isFollowing != true
        val delta = if (follow) 1 else -1
        _uiState.update {
            it.copy(
                isFollowBusy = true,
                profile = profile.copy(isFollowing = follow, followerCount = (profile.followerCount + delta).coerceAtLeast(0)),
            )
        }
        viewModelScope.launch {
            users.setFollowing(profile.id, follow)
                .onSuccess {
                    _uiState.update { it.copy(isFollowBusy = false) }
                    loadProfile(quietly = true)
                }
                .onFailure { e -> _uiState.update { it.copy(isFollowBusy = false, profile = profile, message = e.toUiText()) } }
        }
    }

    fun saveProfile(displayName: String, bio: String) {
        if (_uiState.value.isSavingProfile) return
        if (displayName.trim().length > MAX_NAME || bio.trim().length > MAX_BIO) {
            _uiState.update { it.copy(message = UiText.Res(R.string.profile_error_too_long)) }
            return
        }
        _uiState.update { it.copy(isSavingProfile = true) }
        viewModelScope.launch {
            users.updateMe(displayName, bio)
                .onSuccess { p ->
                    _uiState.update {
                        // PATCH answers without counters; keep the ones we have.
                        val old = it.profile
                        val merged = if (old != null) old.copy(displayName = p.displayName, bio = p.bio) else p
                        it.copy(profile = merged, isSavingProfile = false, profileSaved = true, message = UiText.Res(R.string.profile_saved))
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isSavingProfile = false, message = e.toUiText()) } }
        }
    }

    fun profileSavedHandled() = _uiState.update { it.copy(profileSaved = false) }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    /** Back from a post's detail screen: its counters may have changed. */
    fun refreshPost(postId: String) {
        if (posts.value.items.none { it.id == postId }) return
        viewModelScope.launch {
            content.post(postId).onSuccess { fresh -> postsPager.updateItems { list -> list.map { if (it.id == postId) fresh else it } } }
        }
    }

    fun removePost(postId: String) {
        postsPager.updateItems { list -> list.filterNot { it.id == postId } }
        _uiState.update { s -> s.copy(profile = s.profile?.let { it.copy(postCount = (it.postCount - 1).coerceAtLeast(0)) }) }
    }

    companion object {
        const val MAX_NAME = 50
        const val MAX_BIO = 500

        fun factory(userId: String) = viewModelFactory {
            initializer {
                val container = appContainer
                ProfileViewModel(
                    userId,
                    container.sessions.current()?.userId,
                    container.usersRepository,
                    container.contentRepository,
                    container.favoritesRepository,
                )
            }
        }
    }
}
