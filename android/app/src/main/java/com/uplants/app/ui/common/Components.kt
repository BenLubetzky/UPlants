package com.uplants.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.uplants.app.R
import com.uplants.app.data.model.ContentTypes
import com.uplants.app.data.model.UserSummary

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** Full-area error with a retry button, used when a screen has nothing to show. */
@Composable
fun ErrorBox(message: UiText, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(message.asString(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
fun EmptyBox(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Profile picture, or the first letter of the name when there is none. */
@Composable
fun UserAvatar(user: UserSummary?, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val shape = CircleShape
    if (user?.avatarUrl != null) {
        AsyncImage(
            model = user.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape),
        )
    } else {
        Box(
            modifier = modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (user?.shownName?.firstOrNull() ?: '?').uppercase(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                style = if (size >= 48.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
            )
        }
    }
}

/** Avatar + name + "@username · 2h ago". */
@Composable
fun AuthorRow(user: UserSummary?, createdAt: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        UserAvatar(user)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(user?.shownName ?: stringResource(R.string.deleted_user), style = MaterialTheme.typography.titleSmall)
            Text(
                text = listOfNotNull(user?.let { "@${it.username}" }, relativeTime(createdAt)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Icon followed by a small number, e.g. ♥ 12. */
@Composable
fun CountLabel(icon: ImageVector, text: String, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 0-5 stars. With [onRate] the stars are tappable; tapping the current value again clears it. */
@Composable
fun StarRatingBar(
    stars: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    enabled: Boolean = true,
    onRate: ((Int?) -> Unit)? = null,
) {
    Row(modifier) {
        for (i in 1..5) {
            val filled = stars != null && i <= stars
            Icon(
                imageVector = if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = stringResource(R.string.rate_n_stars, i),
                tint = if (filled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .size(size)
                    .then(
                        if (onRate != null && enabled) Modifier.clickable { onRate(if (stars == i) null else i) } else Modifier
                    ),
            )
        }
    }
}

fun contentTypeIcon(type: String): ImageVector = when (type) {
    ContentTypes.PHOTO -> Icons.Filled.Image
    ContentTypes.VIDEO -> Icons.Filled.Videocam
    ContentTypes.AUDIO -> Icons.Filled.Mic
    else -> Icons.AutoMirrored.Filled.Article
}

@Composable
fun contentTypeLabel(type: String): String = stringResource(
    when (type) {
        ContentTypes.PHOTO -> R.string.type_photo
        ContentTypes.VIDEO -> R.string.type_video
        ContentTypes.AUDIO -> R.string.type_audio
        else -> R.string.type_text
    }
)

/** Shown to visitors when they try something that needs an account. */
@Composable
fun LoginRequiredDialog(onDismiss: () -> Unit, onLogin: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.login_required_title)) },
        text = { Text(stringResource(R.string.login_required_body)) },
        confirmButton = { Button(onClick = onLogin) { Text(stringResource(R.string.sign_in_or_up)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.not_now)) } },
    )
}
