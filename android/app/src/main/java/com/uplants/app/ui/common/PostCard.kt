package com.uplants.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.uplants.app.R
import com.uplants.app.data.model.Category
import com.uplants.app.data.model.ContentTypes
import com.uplants.app.data.model.Post
import com.uplants.app.data.model.UserSummary
import com.uplants.app.ui.theme.UPlantsTheme

/** A post in a list: author, preview, title, category/type and counters. Used by the feed (and later profiles, lists). */
@Composable
fun PostCard(post: Post, onClick: () -> Unit, modifier: Modifier = Modifier, onAuthorClick: ((String) -> Unit)? = null) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        AuthorRow(post.author, post.createdAt, Modifier.fillMaxWidth().padding(12.dp), onClick = onAuthorClick)
        PostPreview(post)
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(post.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (post.contentType == ContentTypes.TEXT && !post.description.isNullOrBlank()) {
                Text(
                    post.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PostTags(post)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CountLabel(Icons.Filled.ThumbUp, compactCount(post.likeCount))
                CountLabel(Icons.AutoMirrored.Filled.Comment, compactCount(post.commentCount))
                CountLabel(Icons.Filled.Star, if (post.ratingCount > 0) formatRating(post.ratingAvg) else "–")
                CountLabel(Icons.Filled.Visibility, compactCount(post.viewCount))
            }
        }
    }
}

/** Category, content type and "Live" chips. */
@Composable
fun PostTags(post: Post, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        post.category?.let { SmallChip(it.name) }
        SmallChip(contentTypeLabel(post.contentType))
        if (post.isLive) {
            SuggestionChip(
                onClick = {},
                label = { Text(stringResource(R.string.live_recording)) },
                icon = { Icon(Icons.Filled.FiberManualRecord, null, Modifier.size(AssistChipDefaults.IconSize), tint = MaterialTheme.colorScheme.error) },
            )
        }
    }
}

@Composable
private fun SmallChip(text: String) {
    SuggestionChip(onClick = {}, label = { Text(text, maxLines = 1) })
}

/** Image for photos (and videos with a thumbnail); an icon tile for videos/audio without one. Nothing for text. */
@Composable
private fun PostPreview(post: Post) {
    val image = post.previewImageUrl
    when {
        image != null -> Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = image,
                contentDescription = post.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(MaterialTheme.colorScheme.surfaceVariant),
            )
            if (post.contentType == ContentTypes.VIDEO) PlayBadge()
        }
        post.contentType == ContentTypes.VIDEO || post.contentType == ContentTypes.AUDIO -> Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 7f).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                contentTypeIcon(post.contentType),
                contentDescription = contentTypeLabel(post.contentType),
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        else -> Unit
    }
}

@Composable
private fun PlayBadge() {
    Icon(
        Icons.Filled.PlayCircle,
        contentDescription = null,
        modifier = Modifier.size(56.dp),
        tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
    )
}

@Preview(showBackground = true)
@Composable
private fun PostCardPreview() {
    UPlantsTheme {
        PostCard(
            post = Post(
                id = "1",
                author = UserSummary("u1", "aly_planta", "Aly"),
                category = Category(1, "Houseplants", "houseplants"),
                contentType = ContentTypes.TEXT,
                source = "live",
                title = "How often should I water my fern?",
                description = "Mine is getting brown tips. Indirect light, watered every 3 days.",
                likeCount = 12, commentCount = 4, ratingAvg = 4.3, ratingCount = 6, viewCount = 1200,
                createdAt = "2026-11-03T16:50:12.345+00:00",
            ),
            onClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
