package com.uplants.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uplants.app.R
import kotlinx.coroutines.flow.distinctUntilChanged

/** Asks for the next page when the last few items of a lazy list become visible. */
@Composable
fun LoadMoreWhenNearEnd(listState: LazyListState, onLoadMore: () -> Unit) {
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) onLoadMore() }
    }
}

/** Last item of a paged list: a spinner while loading, retry after an error, or "all caught up". */
@Composable
fun PagedListFooter(
    isLoadingMore: Boolean,
    loadMoreError: UiText?,
    endReached: Boolean,
    onRetry: () -> Unit,
    endText: String? = stringResource(R.string.feed_end),
) {
    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
        when {
            isLoadingMore -> CircularProgressIndicator(Modifier.size(28.dp))
            loadMoreError != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(loadMoreError.asString(), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            endReached && endText != null -> Text(
                endText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
