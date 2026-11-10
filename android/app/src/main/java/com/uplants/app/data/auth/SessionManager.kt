package com.uplants.app.data.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * The current session, kept in memory as well as on disk. The network layer reads [current]
 * on every request, and a refreshed token must be visible to the next request immediately,
 * not only once DataStore has written it.
 */
class SessionManager(private val store: SessionStore, scope: CoroutineScope) {

    private val _session = MutableStateFlow<Session?>(null)

    /** In-memory session; null until the saved one is loaded. */
    val session: StateFlow<Session?> = _session.asStateFlow()

    /** The saved session straight from disk: emits only once it has been read (used to show a splash, not the login screen). */
    val stored: Flow<Session?> = store.session

    init {
        scope.launch { store.session.collect { _session.value = it } }
    }

    fun current(): Session? = _session.value

    suspend fun save(session: Session) {
        _session.value = session
        store.save(session)
    }

    suspend fun clear() {
        _session.value = null
        store.clear()
    }

    /** For OkHttp's authenticator, which runs on a background thread outside coroutines. */
    fun saveBlocking(session: Session) = runBlocking { save(session) }

    fun clearBlocking() = runBlocking { clear() }
}
