package com.uplants.app.data.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/** Talks to the real backend over REST. */
class NetworkAuthRepository(
    private val api: AuthApi,
    private val store: SessionStore,
) : AuthRepository {

    override val session: Flow<Session?> = store.session

    override suspend fun login(username: String, password: String) =
        authenticate { api.login(LoginRequest(username.trim(), password)) }

    override suspend fun register(username: String, email: String, password: String) =
        authenticate { api.register(RegisterRequest(username.trim(), email.trim(), password)) }

    override suspend fun logout() = store.clear()

    private suspend fun authenticate(call: suspend () -> AuthResponse): Result<Session> =
        try {
            val response = call()
            val session = Session(response.accessToken, response.user.id, response.user.username)
            store.save(session)
            Result.success(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            Result.failure(
                when (e.code()) {
                    401, 403 -> AuthError.InvalidCredentials()
                    409 -> AuthError.AccountExists()
                    else -> AuthError.Server()
                }
            )
        } catch (e: IOException) {
            Result.failure(AuthError.Network())
        } catch (e: SerializationException) {
            Result.failure(AuthError.Server())
        }
}
