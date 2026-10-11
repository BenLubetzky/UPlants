package com.uplants.users.service

import com.uplants.users.models.AuthResponse
import com.uplants.users.models.LoginRequest
import com.uplants.users.models.ProfileDto
import com.uplants.users.models.RegisterRequest
import com.uplants.users.models.SupabaseAuthSuccessResponse
import com.uplants.users.models.SupabaseRpcEmailRequest
import com.uplants.users.models.SupabaseSignUpRequest
import com.uplants.users.models.SupabaseTokenRequest
import com.uplants.users.models.UserDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val status: HttpStatusCode, val message: String) : AuthResult<Nothing>()
}

class SupabaseAuthService(
    private val rawSupabaseUrl: String = System.getenv("SUPABASE_URL") ?: "http://localhost:54321",
    private val publishableKey: String = System.getenv("SUPABASE_PUBLISHABLE_KEY") ?: "",
    private val secretKey: String = System.getenv("SUPABASE_SECRET_KEY") ?: ""
) {
    private val supabaseUrl: String = rawSupabaseUrl
        .trim()
        .trimEnd('/')
        .removeSuffix("/rest/v1")
        .removeSuffix("/auth/v1")
        .trimEnd('/')
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(this@SupabaseAuthService.json)
        }
    }

    /**
     * Registers a new user.
     * 1. Checks if the username is already taken.
     * 2. Calls Supabase Auth sign-up.
     */
    suspend fun register(request: RegisterRequest): AuthResult<AuthResponse> {
        val cleanUsername = request.username.trim()
        val cleanEmail = request.email.trim()

        if (cleanUsername.isBlank() || cleanEmail.isBlank() || request.password.isBlank()) {
            return AuthResult.Error(HttpStatusCode.BadRequest, "All fields are required")
        }

        // 1. Check if username is already in use
        if (isUsernameTaken(cleanUsername)) {
            return AuthResult.Error(HttpStatusCode.Conflict, "Username is already taken")
        }

        // 2. Call Supabase Auth /auth/v1/signup
        val signUpUrl = "$supabaseUrl/auth/v1/signup"
        try {
            val response = client.post(signUpUrl) {
                contentType(ContentType.Application.Json)
                headers {
                    append("apikey", publishableKey)
                }
                setBody(
                    SupabaseSignUpRequest(
                        email = cleanEmail,
                        password = request.password,
                        data = mapOf(
                            "username" to cleanUsername,
                            "display_name" to cleanUsername
                        )
                    )
                )
            }

            if (!response.status.isSuccess()) {
                val errorText = response.bodyAsText()
                if (response.status == HttpStatusCode.BadRequest || response.status == HttpStatusCode.UnprocessableEntity || errorText.contains("already registered", ignoreCase = true)) {
                    return AuthResult.Error(HttpStatusCode.Conflict, "Account with this email or username already exists")
                }
                return AuthResult.Error(response.status, "Registration failed: $errorText")
            }

            val bodyText = response.bodyAsText()
            val supabaseAuth = json.decodeFromString<SupabaseAuthSuccessResponse>(bodyText)

            val accessToken = supabaseAuth.accessToken ?: ""
            val userId = supabaseAuth.user?.id ?: ""

            if (userId.isEmpty()) {
                return AuthResult.Error(HttpStatusCode.InternalServerError, "Failed to retrieve user registration info")
            }

            return AuthResult.Success(
                AuthResponse(
                    accessToken = accessToken,
                    user = UserDto(id = userId, username = cleanUsername)
                )
            )
        } catch (e: Exception) {
            return AuthResult.Error(HttpStatusCode.InternalServerError, e.message ?: "Server connection error")
        }
    }

    /**
     * Logs in a user by username.
     * 1. Resolves username -> email via RPC `email_for_username` or direct email check.
     * 2. Calls Supabase Auth password token endpoint.
     */
    suspend fun login(request: LoginRequest): AuthResult<AuthResponse> {
        val cleanUsername = request.username.trim()
        if (cleanUsername.isBlank() || request.password.isBlank()) {
            return AuthResult.Error(HttpStatusCode.BadRequest, "Username and password are required")
        }

        // 1. Resolve email for username
        var email = getEmailForUsername(cleanUsername)
        if (email == null && cleanUsername.contains("@")) {
            email = cleanUsername // fallback if user typed email as username
        }

        if (email.isNullOrBlank()) {
            return AuthResult.Error(HttpStatusCode.Unauthorized, "Invalid credentials")
        }

        // 2. Authenticate with Supabase Auth /auth/v1/token?grant_type=password
        val tokenUrl = "$supabaseUrl/auth/v1/token?grant_type=password"
        try {
            val response = client.post(tokenUrl) {
                contentType(ContentType.Application.Json)
                headers {
                    append("apikey", publishableKey)
                }
                setBody(
                    SupabaseTokenRequest(
                        email = email,
                        password = request.password
                    )
                )
            }

            if (!response.status.isSuccess()) {
                return AuthResult.Error(HttpStatusCode.Unauthorized, "Invalid credentials")
            }

            val bodyText = response.bodyAsText()
            val supabaseAuth = json.decodeFromString<SupabaseAuthSuccessResponse>(bodyText)

            val accessToken = supabaseAuth.accessToken ?: ""
            val userId = supabaseAuth.user?.id ?: ""

            return AuthResult.Success(
                AuthResponse(
                    accessToken = accessToken,
                    user = UserDto(id = userId, username = cleanUsername)
                )
            )
        } catch (e: Exception) {
            return AuthResult.Error(HttpStatusCode.InternalServerError, e.message ?: "Authentication server error")
        }
    }

    /**
     * Queries public.profiles to check if username exists.
     */
    private suspend fun isUsernameTaken(username: String): Boolean {
        return try {
            val profileUrl = "$supabaseUrl/rest/v1/profiles?username=ilike.${username}"
            val response = client.get(profileUrl) {
                headers {
                    append("apikey", if (secretKey.isNotBlank()) secretKey else publishableKey)
                    if (secretKey.isNotBlank()) {
                        append(HttpHeaders.Authorization, "Bearer $secretKey")
                    }
                }
            }
            if (response.status.isSuccess()) {
                val bodyText = response.bodyAsText()
                val profiles = json.decodeFromString<List<ProfileDto>>(bodyText)
                profiles.isNotEmpty()
            } else false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Calls Supabase RPC function `email_for_username` using the secret key.
     */
    private suspend fun getEmailForUsername(username: String): String? {
        return try {
            val rpcUrl = "$supabaseUrl/rest/v1/rpc/email_for_username"
            val key = if (secretKey.isNotBlank()) secretKey else publishableKey
            val response = client.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                headers {
                    append("apikey", key)
                    append(HttpHeaders.Authorization, "Bearer $key")
                }
                setBody(SupabaseRpcEmailRequest(p_username = username))
            }
            if (response.status.isSuccess()) {
                val text = response.bodyAsText().trim().removeSurrounding("\"")
                if (text.isNotBlank() && text != "null") text else null
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
