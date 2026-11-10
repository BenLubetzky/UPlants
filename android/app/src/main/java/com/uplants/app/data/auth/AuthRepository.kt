package com.uplants.app.data.auth

import androidx.annotation.StringRes
import com.uplants.app.R
import kotlinx.coroutines.flow.Flow

/**
 * A logged-in user. [token] is sent to the services on authenticated requests; it expires after
 * about an hour, and [refreshToken] is traded for a new one (see `network/TokenAuthenticator`).
 */
data class Session(
    val token: String,
    val refreshToken: String?,
    val userId: String,
    val username: String,
)

interface AuthRepository {
    /** Emits the current session, or null when signed out. Survives app restarts. */
    val session: Flow<Session?>

    suspend fun login(username: String, password: String): Result<Session>

    suspend fun register(username: String, email: String, password: String): Result<Session>

    suspend fun logout()
}

/** Failures the UI knows how to explain to the user. */
sealed class AuthError(@StringRes val messageRes: Int) : Exception() {
    class InvalidCredentials : AuthError(R.string.error_invalid_credentials)
    class AccountExists : AuthError(R.string.error_account_exists)
    class EmailNotConfirmed : AuthError(R.string.error_email_not_confirmed)
    class Network : AuthError(R.string.error_network)
    class Server : AuthError(R.string.error_server)
}

/** String resource explaining [this] failure to the user. */
@StringRes
fun Throwable.toMessageRes(): Int = (this as? AuthError)?.messageRes ?: R.string.error_server
