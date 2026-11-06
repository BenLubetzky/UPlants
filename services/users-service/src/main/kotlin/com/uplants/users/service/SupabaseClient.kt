package com.uplants.users.service

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** HTTP access to Supabase (PostgREST, Auth). Only this service holds the keys. */
class SupabaseClient(
    rawUrl: String = System.getenv("SUPABASE_URL") ?: "http://localhost:54321",
    val publishableKey: String = System.getenv("SUPABASE_PUBLISHABLE_KEY") ?: "",
    private val secretKey: String = System.getenv("SUPABASE_SECRET_KEY") ?: "",
) {
    // .env may hold the URL with /rest/v1 at the end.
    val baseUrl: String = rawUrl.trim().trimEnd('/')
        .removeSuffix("/rest/v1").removeSuffix("/auth/v1").trimEnd('/')

    val restUrl = "$baseUrl/rest/v1"
    val authUrl = "$baseUrl/auth/v1"

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    val http = HttpClient(CIO) {
        install(ContentNegotiation) { json(this@SupabaseClient.json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 15_000
        }
    }

    /** Authenticates the request as the service (full access, bypasses RLS). */
    fun HttpRequestBuilder.asService() {
        header("apikey", secretKey)
        header(HttpHeaders.Authorization, "Bearer $secretKey")
    }

    /** Number of rows in [table] matching [filters] (PostgREST filter syntax), without fetching them. */
    suspend fun count(table: String, vararg filters: Pair<String, String>): Int? {
        val response = http.head("$restUrl/$table") {
            asService()
            header("Prefer", "count=exact")
            parameter("select", "*")
            filters.forEach { (key, value) -> parameter(key, value) }
        }
        if (!response.status.isSuccess()) return null
        // Content-Range: 0-24/57  or  */0
        return response.headers[HttpHeaders.ContentRange]?.substringAfter('/')?.toIntOrNull()
    }
}
