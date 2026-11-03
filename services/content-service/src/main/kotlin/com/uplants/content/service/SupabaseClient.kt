package com.uplants.content.service

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

const val MEDIA_BUCKET = "post-media"

/** HTTP access to Supabase (PostgREST, Auth, Storage). Only this service holds the keys. */
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
    val storageUrl = "$baseUrl/storage/v1"

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false // leave null fields out of inserts so DB defaults apply
    }

    val http = HttpClient(CIO) {
        install(ContentNegotiation) { json(this@SupabaseClient.json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 60_000 // uploads up to 50 MB
        }
    }

    /** Authenticates the request as the service (full access, bypasses RLS). */
    fun HttpRequestBuilder.asService() {
        header("apikey", secretKey)
        header(HttpHeaders.Authorization, "Bearer $secretKey")
    }

    fun publicMediaUrl(path: String) = "$storageUrl/object/public/$MEDIA_BUCKET/$path"

    /** Storage path of a file we uploaded, or null if the URL isn't ours. */
    fun mediaPathOf(url: String): String? =
        url.removePrefix("$storageUrl/object/public/$MEDIA_BUCKET/").takeIf { it != url }
}
