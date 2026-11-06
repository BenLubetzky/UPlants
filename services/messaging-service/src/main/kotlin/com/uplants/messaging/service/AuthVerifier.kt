package com.uplants.messaging.service

import com.uplants.messaging.models.SupabaseUser
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess

/** Checks the access token the app got from users-service at login. */
class AuthVerifier(private val supabase: SupabaseClient) {

    /** The user id, or null if the token is invalid or expired. */
    suspend fun userIdFor(token: String): String? {
        val response = supabase.http.get("${supabase.authUrl}/user") {
            header("apikey", supabase.publishableKey)
            bearerAuth(token)
        }
        return if (response.status.isSuccess()) response.body<SupabaseUser>().id else null
    }
}
