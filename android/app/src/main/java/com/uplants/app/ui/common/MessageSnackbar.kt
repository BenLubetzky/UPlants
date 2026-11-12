package com.uplants.app.ui.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/** Shows [message] once in a snackbar, then calls [onShown]. */
@Composable
fun rememberMessageSnackbar(message: UiText?, onShown: () -> Unit): SnackbarHostState {
    val snackbar = remember { SnackbarHostState() }
    val text = message?.asString()
    LaunchedEffect(text) {
        if (text != null) {
            snackbar.showSnackbar(text)
            onShown()
        }
    }
    return snackbar
}
