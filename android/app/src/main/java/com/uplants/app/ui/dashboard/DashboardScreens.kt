package com.uplants.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uplants.app.R
import com.uplants.app.appContainer
import com.uplants.app.data.interactions.InteractionsRepository
import com.uplants.app.data.model.Post
import com.uplants.app.data.model.PostStats
import com.uplants.app.ui.common.EmptyBox
import com.uplants.app.ui.common.ErrorBox
import com.uplants.app.ui.common.LoadingBox
import com.uplants.app.ui.common.UiText
import com.uplants.app.ui.common.compactCount
import com.uplants.app.ui.common.contentTypeLabel
import com.uplants.app.ui.common.formatRating
import com.uplants.app.ui.common.relativeTime
import com.uplants.app.ui.common.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    userId: String,
    onBack: () -> Unit,
    onOpenPostStats: (postId: String, title: String) -> Unit,
    viewModel: DashboardViewModel = viewModel(key = "dashboard-$userId", factory = DashboardViewModel.factory(userId)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            state.isLoading && state.posts.isEmpty() -> LoadingBox(modifier)
            state.error != null -> ErrorBox(state.error!!, viewModel::load, modifier)
            state.posts.isEmpty() -> EmptyBox(stringResource(R.string.dashboard_empty), modifier)
            else -> DashboardContent(state, onOpenPostStats, modifier)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardContent(state: DashboardUiState, onOpenPostStats: (String, String) -> Unit, modifier: Modifier) {
    val t = state.totals
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "tiles") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(compactCount(t.posts), stringResource(R.string.profile_stat_posts))
                StatTile(compactCount(t.views), stringResource(R.string.dashboard_views))
                StatTile(compactCount(t.likes), stringResource(R.string.dashboard_likes))
                StatTile(compactCount(t.comments), stringResource(R.string.dashboard_comments))
                StatTile(compactCount(t.shares), stringResource(R.string.dashboard_shares))
                StatTile(if (t.ratingCount > 0) formatRating(t.ratingAvg) + "★" else "–", stringResource(R.string.dashboard_avg_rating, t.ratingCount))
            }
        }
        if (state.chartsUnavailable) {
            item(key = "charts-unavailable") {
                Text(stringResource(R.string.dashboard_charts_unavailable), color = MaterialTheme.colorScheme.error)
            }
        } else {
            item(key = "views-chart") {
                ChartCard(stringResource(R.string.dashboard_views_30_days)) { ViewsPerDayChart(state.viewsByDay) }
            }
            item(key = "rating-chart") {
                ChartCard(stringResource(R.string.dashboard_ratings)) { RatingHistogramChart(state.ratingHistogram) }
            }
        }
        item(key = "history-title") {
            Text(stringResource(R.string.dashboard_history), style = MaterialTheme.typography.titleMedium)
        }
        items(state.posts, key = { it.id }) { post ->
            PostStatsRow(post, onClick = { onOpenPostStats(post.id, post.title) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun StatTile(value: String, label: String) {
    Card(Modifier.width(104.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

/** One published post in the history: title, when, type, and its main numbers. */
@Composable
private fun PostStatsRow(post: Post, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(post.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${relativeTime(post.createdAt)} · ${contentTypeLabel(post.contentType)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 4.dp)) {
            Text(stringResource(R.string.dashboard_row_views, compactCount(post.viewCount)), style = MaterialTheme.typography.labelMedium)
            Text(stringResource(R.string.dashboard_row_likes, compactCount(post.likeCount)), style = MaterialTheme.typography.labelMedium)
            Text(stringResource(R.string.dashboard_row_comments, compactCount(post.commentCount)), style = MaterialTheme.typography.labelMedium)
            Text(if (post.ratingCount > 0) formatRating(post.ratingAvg) + "★" else "–★", style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ---- One post's statistics ---------------------------------------------------------

data class PostStatsUiState(val stats: PostStats? = null, val isLoading: Boolean = true, val error: UiText? = null)

class PostStatsViewModel(private val postId: String, private val interactions: InteractionsRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(PostStatsUiState())
    val uiState: StateFlow<PostStatsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = PostStatsUiState(stats = _uiState.value.stats, isLoading = true)
        viewModelScope.launch {
            interactions.stats(postId)
                .onSuccess { _uiState.value = PostStatsUiState(stats = it, isLoading = false) }
                .onFailure { _uiState.value = PostStatsUiState(isLoading = false, error = it.toUiText()) }
        }
    }

    companion object {
        fun factory(postId: String) = viewModelFactory {
            initializer { PostStatsViewModel(postId, appContainer.interactionsRepository) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PostStatsScreen(
    postId: String,
    title: String,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    viewModel: PostStatsViewModel = viewModel(key = "stats-$postId", factory = PostStatsViewModel.factory(postId)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        val stats = state.stats
        when {
            stats == null && state.isLoading -> LoadingBox(modifier)
            stats == null -> ErrorBox(state.error ?: UiText.Res(R.string.error_server), viewModel::load, modifier)
            else -> LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(compactCount(stats.views), stringResource(R.string.dashboard_views))
                        StatTile(compactCount(stats.likes), stringResource(R.string.dashboard_likes))
                        StatTile(compactCount(stats.dislikes), stringResource(R.string.dashboard_dislikes))
                        StatTile(compactCount(stats.comments), stringResource(R.string.dashboard_comments))
                        StatTile(
                            if (stats.ratingCount > 0) formatRating(stats.ratingAvg) + "★" else "–",
                            stringResource(R.string.dashboard_avg_rating, stats.ratingCount),
                        )
                    }
                }
                item { ChartCard(stringResource(R.string.dashboard_views_30_days)) { ViewsPerDayChart(stats.viewsByDay) } }
                item { ChartCard(stringResource(R.string.dashboard_ratings)) { RatingHistogramChart(stats.ratingHistogram) } }
                item {
                    Text(
                        stringResource(R.string.dashboard_open_post),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onOpenPost(postId) }.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}
