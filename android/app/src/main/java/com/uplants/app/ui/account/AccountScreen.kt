package com.uplants.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uplants.app.R
import com.uplants.app.data.model.UserSummary
import com.uplants.app.ui.common.UserAvatar
import com.uplants.app.ui.theme.UPlantsTheme

/**
 * Account tab: who is logged in, and log out (or log in for visitors).
 * Becomes the full profile screen in a later step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(username: String?, onSignOut: () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_account)) }) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            UserAvatar(username?.let { UserSummary(id = "", username = it) }, size = 72.dp)
            Text(
                text = if (username != null) stringResource(R.string.home_welcome, username) else stringResource(R.string.home_guest),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(if (username != null) R.string.account_more_soon else R.string.account_guest_hint),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (username != null) {
                OutlinedButton(onClick = onSignOut) { Text(stringResource(R.string.logout)) }
            } else {
                Button(onClick = onSignOut) { Text(stringResource(R.string.sign_in_or_up)) }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountPreview() {
    UPlantsTheme { AccountScreen(username = "demo", onSignOut = {}) }
}
