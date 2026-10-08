package com.uplants.app.ui.auth.signup

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uplants.app.R
import com.uplants.app.ui.auth.AuthScaffold
import com.uplants.app.ui.auth.AuthTextField
import com.uplants.app.ui.auth.FormErrorText
import com.uplants.app.ui.auth.LoadingButton
import com.uplants.app.ui.auth.PasswordField
import com.uplants.app.ui.auth.SignUpFieldErrors
import com.uplants.app.ui.theme.UPlantsTheme

@Composable
fun SignUpScreen(
    onNavigateToLogin: () -> Unit,
    viewModel: SignUpViewModel = viewModel(factory = SignUpViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SignUpContent(
        state = state,
        onUsernameChange = viewModel::onUsernameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
        onSubmit = viewModel::signUp,
        onNavigateToLogin = onNavigateToLogin,
    )
}

@Composable
fun SignUpContent(
    state: SignUpUiState,
    onUsernameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    val errors = state.fieldErrors
    AuthScaffold(title = stringResource(R.string.signup_title)) {
        AuthTextField(
            value = state.username,
            onValueChange = onUsernameChange,
            label = stringResource(R.string.field_username),
            errorText = errors.username?.let { stringResource(it) },
            enabled = !state.isLoading,
        )
        AuthTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = stringResource(R.string.field_email),
            errorText = errors.email?.let { stringResource(it) },
            keyboardType = KeyboardType.Email,
            enabled = !state.isLoading,
        )
        PasswordField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = stringResource(R.string.field_password),
            errorText = errors.password?.let { stringResource(it) },
            imeAction = ImeAction.Next,
            enabled = !state.isLoading,
        )
        PasswordField(
            value = state.confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = stringResource(R.string.field_confirm_password),
            errorText = errors.confirmPassword?.let { stringResource(it) },
            onDone = onSubmit,
            enabled = !state.isLoading,
        )
        state.errorRes?.let { FormErrorText(stringResource(it)) }
        LoadingButton(
            text = stringResource(R.string.signup_button),
            loading = state.isLoading,
            enabled = state.canSubmit,
            onClick = onSubmit,
        )
        TextButton(onClick = onNavigateToLogin, enabled = !state.isLoading) {
            Text(stringResource(R.string.signup_have_account))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SignUpPreview() {
    UPlantsTheme {
        SignUpContent(
            state = SignUpUiState(
                username = "ab",
                email = "plant@lover",
                fieldErrors = SignUpFieldErrors(
                    username = R.string.error_username_invalid,
                    email = R.string.error_email_invalid,
                ),
            ),
            onUsernameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onSubmit = {},
            onNavigateToLogin = {},
        )
    }
}
