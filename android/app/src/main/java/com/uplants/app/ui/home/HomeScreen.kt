package com.uplants.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uplants.app.R
import com.uplants.app.ui.theme.UPlantsTheme

/** Placeholder landing screen after login; replace with the real app content. */
@Composable
fun HomeScreen(username: String?, onSignOut: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = if (username != null) stringResource(R.string.home_welcome, username) else stringResource(R.string.home_guest),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.home_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onSignOut) {
            Text(stringResource(if (username != null) R.string.logout else R.string.sign_in_or_up))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    UPlantsTheme { HomeScreen(username = "demo", onSignOut = {}) }
}
