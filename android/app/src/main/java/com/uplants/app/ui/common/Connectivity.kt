package com.uplants.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uplants.app.R
import com.uplants.app.data.network.NetworkStatus
import com.uplants.app.data.network.Service

/** The current connection, provided by MainScreen so any screen can adapt (requirements 23, 24). */
val LocalNetworkStatus = compositionLocalOf { NetworkStatus() }

/**
 * Thin bar shown above the bottom navigation: "You're offline" (requirement 23), or which parts of the app are
 * unavailable because their service is down (requirement 25). Nothing when everything is fine.
 */
@Composable
fun ConnectionBanner(status: NetworkStatus, servicesDown: Set<Service>, modifier: Modifier = Modifier) {
    val (icon, text) = when {
        !status.isOnline -> Icons.Filled.WifiOff to stringResource(R.string.banner_offline)
        servicesDown.size == Service.entries.size -> Icons.Filled.CloudOff to stringResource(R.string.banner_all_down)
        servicesDown.isNotEmpty() -> Icons.Filled.CloudOff to stringResource(
            R.string.banner_some_down,
            servicesDown.sortedBy { it.ordinal }.map { serviceLabel(it) }.joinToString(", "),
        )
        else -> return
    }
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

/** What each service means to a user. */
@Composable
fun serviceLabel(service: Service): String = stringResource(
    when (service) {
        Service.USERS -> R.string.service_users
        Service.CONTENT -> R.string.service_content
        Service.INTERACTIONS -> R.string.service_interactions
        Service.FAVORITES -> R.string.service_favorites
        Service.MESSAGING -> R.string.service_messaging
    }
)

/** "Saved 5 min. ago": shown when a screen is displaying content from the offline cache. */
@Composable
fun SavedContentNote(savedAt: Long, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CloudOff, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.saved_content, relativeTimeMillis(savedAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
