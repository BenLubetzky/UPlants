package com.uplants.app.data.network

import com.uplants.app.data.auth.AuthApi
import com.uplants.app.data.auth.RefreshRequest
import com.uplants.app.data.auth.Session
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Supabase access tokens expire after about an hour. When a service answers 401, OkHttp calls this:
 * trade the refresh token for a new access token and retry the request once. If that fails the
 * session is over, so it's cleared and the app goes back to the login screen.
 */
class TokenAuthenticator(
    private val currentSession: () -> Session?,
    private val authApi: () -> AuthApi,
    private val saveSession: (Session) -> Unit,
    private val endSession: () -> Unit,
) : Authenticator {

    @Synchronized
    override fun authenticate(route: Route?, response: Response): Request? {
        val sentToken = response.request.header(AuthInterceptor.AUTHORIZATION)?.removePrefix("Bearer ")
            ?: return null // Not an authenticated request (e.g. a wrong password at login).
        if (response.priorResponse != null) return null // Already retried once.
        val session = currentSession() ?: return null

        // Another request may have refreshed the token while this one was in flight.
        if (session.token != sentToken) return response.request.withToken(session.token)

        val refreshToken = session.refreshToken
        val refreshed = refreshToken?.let {
            runCatching { authApi().refresh(RefreshRequest(it)).execute().body() }.getOrNull()
        }
        if (refreshed == null) {
            endSession()
            return null
        }
        val newSession = session.copy(
            token = refreshed.accessToken,
            refreshToken = refreshed.refreshToken ?: refreshToken,
        )
        saveSession(newSession)
        return response.request.withToken(newSession.token)
    }

    private fun Request.withToken(token: String) =
        newBuilder().header(AuthInterceptor.AUTHORIZATION, "Bearer $token").build()
}
