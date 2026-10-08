package com.uplants.app.ui.auth.signup

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uplants.app.appContainer
import com.uplants.app.data.auth.AuthRepository
import com.uplants.app.data.auth.toMessageRes
import com.uplants.app.ui.auth.AuthValidator
import com.uplants.app.ui.auth.SignUpFieldErrors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignUpUiState(
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val fieldErrors: SignUpFieldErrors = SignUpFieldErrors(),
    val isLoading: Boolean = false,
    @StringRes val errorRes: Int? = null,
) {
    val canSubmit: Boolean
        get() = !isLoading && listOf(username, email, password, confirmPassword).all { it.isNotEmpty() }
}

class SignUpViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> = _uiState.asStateFlow()

    // Editing a field clears that field's error, so messages don't linger while the user fixes them.
    fun onUsernameChange(value: String) = _uiState.update {
        it.copy(username = value, fieldErrors = it.fieldErrors.copy(username = null), errorRes = null)
    }

    fun onEmailChange(value: String) = _uiState.update {
        it.copy(email = value, fieldErrors = it.fieldErrors.copy(email = null), errorRes = null)
    }

    fun onPasswordChange(value: String) = _uiState.update {
        it.copy(password = value, fieldErrors = it.fieldErrors.copy(password = null), errorRes = null)
    }

    fun onConfirmPasswordChange(value: String) = _uiState.update {
        it.copy(confirmPassword = value, fieldErrors = it.fieldErrors.copy(confirmPassword = null), errorRes = null)
    }

    fun signUp() {
        val state = _uiState.value
        if (!state.canSubmit) return
        val errors = AuthValidator.validateSignUp(state.username, state.email, state.password, state.confirmPassword)
        if (errors.hasErrors) {
            _uiState.update { it.copy(fieldErrors = errors) }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorRes = null) }
        viewModelScope.launch {
            val result = authRepository.register(state.username, state.email, state.password)
            _uiState.update {
                it.copy(isLoading = false, errorRes = result.exceptionOrNull()?.toMessageRes())
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SignUpViewModel(appContainer.authRepository) }
        }
    }
}
