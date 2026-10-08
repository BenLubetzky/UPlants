package com.uplants.app.ui.auth.login

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uplants.app.appContainer
import com.uplants.app.data.auth.AuthRepository
import com.uplants.app.data.auth.toMessageRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    @StringRes val errorRes: Int? = null,
) {
    val canSubmit: Boolean
        get() = username.isNotBlank() && password.isNotEmpty() && !isLoading
}

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value, errorRes = null) }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, errorRes = null) }

    /** On success the session flow emits and the app leaves the auth screens on its own. */
    fun login() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.update { it.copy(isLoading = true, errorRes = null) }
        viewModelScope.launch {
            val result = authRepository.login(state.username, state.password)
            _uiState.update {
                it.copy(isLoading = false, errorRes = result.exceptionOrNull()?.toMessageRes())
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { LoginViewModel(appContainer.authRepository) }
        }
    }
}
