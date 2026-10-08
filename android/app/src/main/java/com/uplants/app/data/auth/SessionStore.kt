package com.uplants.app.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "session")

/** Persists the session on the device so the user stays logged in. */
class SessionStore(private val context: Context) {

    val session: Flow<Session?> = context.sessionDataStore.data.map { prefs ->
        val token = prefs[TOKEN] ?: return@map null
        Session(token = token, userId = prefs[USER_ID] ?: 0L, username = prefs[USERNAME].orEmpty())
    }

    suspend fun save(session: Session) {
        context.sessionDataStore.edit { prefs ->
            prefs[TOKEN] = session.token
            prefs[USER_ID] = session.userId
            prefs[USERNAME] = session.username
        }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }

    private companion object {
        val TOKEN = stringPreferencesKey("token")
        val USER_ID = longPreferencesKey("user_id")
        val USERNAME = stringPreferencesKey("username")
    }
}
