package com.uplants.app.ui.auth.login

import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uplants.app.R
import com.uplants.app.ui.auth.AuthScaffold
import com.uplants.app.ui.auth.AuthTextField
import com.uplants.app.ui.auth.FormErrorText
import com.uplants.app.ui.auth.LoadingButton
import com.uplants.app.ui.auth.PasswordField
import com.uplants.app.ui.theme.UPlantsTheme

@Composable
fun LoginScreen(
    onNavigateToSignUp: () -> Unit,
    onContinueAsGuest: () -> Unit,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoginContent(
        state = state,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::login,
        onNavigateToSignUp = onNavigateToSignUp,
        onContinueAsGuest = onContinueAsGuest,
    )
}

/** Stateless UI, so it can be previewed and tested without a ViewModel. */
@Composable
fun LoginContent(
    state: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    onContinueAsGuest: () -> Unit,
) {
    AuthScaffold(title = stringResource(R.string.login_title)) {
        AuthTextField(
            value = state.username,
            onValueChange = onUsernameChange,
            label = stringResource(R.string.field_username),
            enabled = !state.isLoading,
        )
        PasswordField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = stringResource(R.string.field_password),
            onDone = onSubmit,
            enabled = !state.isLoading,
        )
        state.errorRes?.let { FormErrorText(stringResource(it)) }
        LoadingButton(
            text = stringResource(R.string.login_button),
            loading = state.isLoading,
            enabled = state.canSubmit,
            onClick = onSubmit,
        )
        TextButton(onClick = onNavigateToSignUp, enabled = !state.isLoading) {
            Text(stringResource(R.string.login_no_account))
        }
        TextButton(onClick = onContinueAsGuest, enabled = !state.isLoading) {
            Text(stringResource(R.string.continue_as_guest))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginPreview() {
    UPlantsTheme {
        LoginContent(
            state = LoginUiState(username = "demo", errorRes = R.string.error_invalid_credentials),
            onUsernameChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onNavigateToSignUp = {},
            onContinueAsGuest = {},
        )
    }
}
