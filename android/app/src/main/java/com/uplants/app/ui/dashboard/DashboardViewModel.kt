package com.uplants.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uplants.app.appContainer
import com.uplants.app.data.content.ContentRepository
import com.uplants.app.data.content.PostFilter
import com.uplants.app.data.content.PostSort
import com.uplants.app.data.interactions.InteractionsRepository
import com.uplants.app.data.model.DayCount
import com.uplants.app.data.model.Post
import com.uplants.app.data.model.PostStats
import com.uplants.app.ui.common.UiText
import com.uplants.app.ui.common.toUiText
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Totals over all of the user's posts. */
data class DashboardTotals(
    val posts: Int = 0,
    val views: Int = 0,
    val likes: Int = 0,
    val dislikes: Int = 0,
    val comments: Int = 0,
    val shares: Int = 0,
    val ratingCount: Int = 0,
    /** Average of all ratings received (weighted by how many each post got). */
    val ratingAvg: Double = 0.0,
)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val error: UiText? = null,
    /** History of published posts, newest first. */
    val posts: List<Post> = emptyList(),
    val totals: DashboardTotals = DashboardTotals(),
    /** Views per day over the last 30 days, all posts together. */
    val viewsByDay: List<DayCount> = emptyList(),
    /** Ratings received, by stars (0-5). */
    val ratingHistogram: List<Int> = List(6) { 0 },
    /** The per-day / histogram charts could not be loaded (interactions-service down); totals still show. */
    val chartsUnavailable: Boolean = false,
)

/**
 * Statistics dashboard (requirement 19): the user's published posts with their numbers. Totals come from
 * the posts themselves (content-service); views per day and the rating histogram are added up from each
 * post's stats (interactions-service).
 */
class DashboardViewModel(
    private val userId: String,
    private val content: ContentRepository,
    private val interactions: InteractionsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val posts = loadAllPosts().getOrElse { e ->
                _uiState.update { it.copy(isLoading = false, error = e.toUiText()) }
                return@launch
            }
            _uiState.update { it.copy(posts = posts, totals = totalsOf(posts)) }

            // One stats call per post (the most recent ones), in parallel.
            val stats = posts.take(MAX_STATS_POSTS)
                .map { post -> async { interactions.stats(post.id) } }
                .awaitAll()
            val ok = stats.mapNotNull { it.getOrNull() }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    viewsByDay = sumViewsByDay(ok),
                    ratingHistogram = sumHistograms(ok),
                    chartsUnavailable = posts.isNotEmpty() && ok.isEmpty(),
                )
            }
        }
    }

    /** All of the user's posts, page by page (capped, a demo account won't have more). */
    private suspend fun loadAllPosts(): Result<List<Post>> {
        val all = mutableListOf<Post>()
        while (all.size < MAX_POSTS) {
            val page = content.posts(PostFilter(authorId = userId, sort = PostSort.RECENT), PAGE, all.size)
                .getOrElse { return Result.failure(it) }
            all += page.posts
            if (page.posts.size < PAGE) break
        }
        return Result.success(all)
    }

    companion object {
        private const val PAGE = 50
        private const val MAX_POSTS = 500
        private const val MAX_STATS_POSTS = 30

        fun factory(userId: String) = viewModelFactory {
            initializer { DashboardViewModel(userId, appContainer.contentRepository, appContainer.interactionsRepository) }
        }
    }
}

internal fun totalsOf(posts: List<Post>): DashboardTotals {
    val ratingCount = posts.sumOf { it.ratingCount }
    val ratingSum = posts.sumOf { it.ratingAvg * it.ratingCount }
    return DashboardTotals(
        posts = posts.size,
        views = posts.sumOf { it.viewCount },
        likes = posts.sumOf { it.likeCount },
        dislikes = posts.sumOf { it.dislikeCount },
        comments = posts.sumOf { it.commentCount },
        shares = posts.sumOf { it.shareCount },
        ratingCount = ratingCount,
        ratingAvg = if (ratingCount > 0) ratingSum / ratingCount else 0.0,
    )
}

/** Adds up the per-day views of several posts; every post reports the same 30 days. */
internal fun sumViewsByDay(stats: List<PostStats>): List<DayCount> {
    val byDate = sortedMapOf<String, Int>()
    stats.forEach { s -> s.viewsByDay.forEach { d -> byDate[d.date] = (byDate[d.date] ?: 0) + d.count } }
    return byDate.map { (date, count) -> DayCount(date, count) }
}

internal fun sumHistograms(stats: List<PostStats>): List<Int> =
    List(6) { stars -> stats.sumOf { it.ratingHistogram.getOrElse(stars) { 0 } } }
