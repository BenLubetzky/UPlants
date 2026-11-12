package com.uplants.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uplants.app.appContainer
import com.uplants.app.data.auth.AuthRepository
import com.uplants.app.data.auth.Session
import com.uplants.app.data.notifications.ActivityChecker
import com.uplants.app.data.notifications.NotificationScheduler
import com.uplants.app.data.offline.OfflineCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AuthState {
    /** Reading the saved session from disk; show a spinner instead of flashing the login screen. */
    data object Loading : AuthState
    data object SignedOut : AuthState
    /** Unregistered visitor: can browse but not publish, comment, etc. */
    data object Guest : AuthState
    data class SignedIn(val session: Session) : AuthState
}

/** Decides at the top level whether the user sees the auth screens or the app. */
class AppViewModel(
    private val authRepository: AuthRepository,
    private val scheduler: NotificationScheduler,
    private val activityChecker: ActivityChecker,
    private val offlineCache: OfflineCache,
) : ViewModel() {

    private val isGuest = MutableStateFlow(false)

    val authState: StateFlow<AuthState> =
        combine(authRepository.session, isGuest) { session, guest ->
            when {
                session != null -> AuthState.SignedIn(session)
                guest -> AuthState.Guest
                else -> AuthState.SignedOut
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.Loading)

    fun continueAsGuest() {
        isGuest.value = true
    }

    /** Logs out a registered user, or sends a guest back to the login screen. */
    fun signOut() {
        isGuest.value = false
        scheduler.cancel()
        viewModelScope.launch {
            activityChecker.reset()
            offlineCache.clear() // saved content belongs to the account that logged out
            authRepository.logout()
        }
    }

    /** After login: check for new activity now and every 15 minutes, even with the app closed. */
    fun startNotifications() {
        scheduler.schedulePeriodic()
        scheduler.checkNow()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer
                AppViewModel(container.authRepository, container.notificationScheduler, container.activityChecker, container.offlineCache)
            }
        }
    }
}
