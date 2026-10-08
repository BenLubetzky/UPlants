package com.uplants.app.data.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * In-memory stand-in for the backend so the UI can be built and demoed before the
 * real API exists. Accounts are lost when the app process dies.
 * Enabled with `uplants.useFakeBackend=true` in gradle.properties.
 */
class FakeAuthRepository(private val store: SessionStore) : AuthRepository {

    private data class Account(val id: Long, val email: String, val password: String)

    private val mutex = Mutex()
    private val accounts = mutableMapOf(
        "demo" to Account(id = 1, email = "demo@uplants.app", password = "password123"),
    )

    override val session: Flow<Session?> = store.session

    override suspend fun login(username: String, password: String): Result<Session> {
        delay(FAKE_LATENCY_MS)
        val name = username.trim()
        val account = mutex.withLock { accounts[name] }
        if (account == null || account.password != password) {
            return Result.failure(AuthError.InvalidCredentials())
        }
        return Result.success(startSession(account.id, name))
    }

    override suspend fun register(username: String, email: String, password: String): Result<Session> {
        delay(FAKE_LATENCY_MS)
        val name = username.trim()
        val mail = email.trim()
        val account = mutex.withLock {
            val taken = name in accounts || accounts.values.any { it.email.equals(mail, ignoreCase = true) }
            if (taken) return Result.failure(AuthError.AccountExists())
            Account(id = accounts.size + 1L, email = mail, password = password).also { accounts[name] = it }
        }
        return Result.success(startSession(account.id, name))
    }

    override suspend fun logout() = store.clear()

    private suspend fun startSession(userId: Long, username: String): Session =
        Session(token = "fake-${UUID.randomUUID()}", userId = userId, username = username)
            .also { store.save(it) }

    private companion object {
        const val FAKE_LATENCY_MS = 600L
    }
}
