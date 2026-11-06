package com.uplants.users.service

import com.uplants.users.models.AuthResponse
import com.uplants.users.models.LoginRequest
import com.uplants.users.models.RegisterRequest
import com.uplants.users.models.SupabaseAuthSuccessResponse
import com.uplants.users.models.SupabaseRefreshRequest
import com.uplants.users.models.SupabaseRpcEmailRequest
import com.uplants.users.models.SupabaseSignUpRequest
import com.uplants.users.models.SupabaseTokenRequest
import com.uplants.users.models.UserDto
import com.uplants.users.models.UsernameRow
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import org.slf4j.LoggerFactory

/** Sign-up, log-in and token refresh through Supabase Auth. */
class SupabaseAuthService(private val supabase: SupabaseClient) {

    private val log = LoggerFactory.getLogger(SupabaseAuthService::class.java)

    private val invalidCredentials = ServiceResult.Fail(HttpStatusCode.Unauthorized, "Invalid credentials")

    /**
     * Registers a new user.
     * 1. Checks if the username is already taken.
     * 2. Calls Supabase Auth sign-up; a trigger creates the `profiles` row.
     */
    suspend fun register(request: RegisterRequest): ServiceResult<AuthResponse> = with(supabase) {
        val cleanUsername = request.username.trim()
        val cleanEmail = request.email.trim()

        if (cleanUsername.isBlank() || cleanEmail.isBlank() || request.password.isBlank()) {
            return ServiceResult.Fail(HttpStatusCode.BadRequest, "All fields are required")
        }

        if (isUsernameTaken(cleanUsername)) {
            return ServiceResult.Fail(HttpStatusCode.Conflict, "Username is already taken")
        }

        val response = http.post("$authUrl/signup") {
            header("apikey", publishableKey)
            contentType(ContentType.Application.Json)
            setBody(
                SupabaseSignUpRequest(
                    email = cleanEmail,
                    password = request.password,
                    data = mapOf("username" to cleanUsername, "display_name" to cleanUsername)
                )
            )
        }

        if (!response.status.isSuccess()) {
            val errorText = response.bodyAsText()
            if (response.status == HttpStatusCode.BadRequest || response.status == HttpStatusCode.UnprocessableEntity ||
                errorText.contains("already registered", ignoreCase = true)
            ) {
                return ServiceResult.Fail(HttpStatusCode.Conflict, "Account with this email or username already exists")
            }
            log.error("Sign-up failed {}: {}", response.status, errorText)
            return ServiceResult.Fail(HttpStatusCode.BadGateway, "Registration failed")
        }

        val auth = response.body<SupabaseAuthSuccessResponse>()
        val userId = auth.user?.id
            ?: return ServiceResult.Fail(HttpStatusCode.InternalServerError, "Failed to retrieve user registration info")

        ServiceResult.Ok(
            AuthResponse(
                accessToken = auth.accessToken.orEmpty(),
                refreshToken = auth.refreshToken,
                user = UserDto(id = userId, username = cleanUsername)
            ),
            HttpStatusCode.Created
        )
    }

    /**
     * Logs in by username (or email).
     * 1. Resolves username -> email with the `email_for_username` function.
     * 2. Calls the Supabase Auth password grant.
     */
    suspend fun login(request: LoginRequest): ServiceResult<AuthResponse> = with(supabase) {
        val cleanUsername = request.username.trim()
        if (cleanUsername.isBlank() || request.password.isBlank()) {
            return ServiceResult.Fail(HttpStatusCode.BadRequest, "Username and password are required")
        }

        val email = getEmailForUsername(cleanUsername)
            ?: cleanUsername.takeIf { it.contains("@") } // fallback if user typed their email
            ?: return invalidCredentials

        val response = http.post("$authUrl/token") {
            parameter("grant_type", "password")
            header("apikey", publishableKey)
            contentType(ContentType.Application.Json)
            setBody(SupabaseTokenRequest(email = email, password = request.password))
        }
        if (!response.status.isSuccess()) return invalidCredentials
        authResponse(response.body())
    }

    /** Swaps a refresh token for a new access token (and a new refresh token). */
    suspend fun refresh(refreshToken: String): ServiceResult<AuthResponse> = with(supabase) {
        if (refreshToken.isBlank()) return ServiceResult.Fail(HttpStatusCode.BadRequest, "refresh_token is required")
        val response = http.post("$authUrl/token") {
            parameter("grant_type", "refresh_token")
            header("apikey", publishableKey)
            contentType(ContentType.Application.Json)
            setBody(SupabaseRefreshRequest(refreshToken))
        }
        if (!response.status.isSuccess()) return ServiceResult.Fail(HttpStatusCode.Unauthorized, "Session expired, log in again")
        authResponse(response.body())
    }

    /** Builds the answer with the username stored in the profile (the user may have typed it in another case, or their email). */
    private suspend fun authResponse(auth: SupabaseAuthSuccessResponse): ServiceResult<AuthResponse> {
        val userId = auth.user?.id ?: return invalidCredentials
        val username = usernameOf(userId) ?: return USER_NOT_FOUND
        return ServiceResult.Ok(
            AuthResponse(
                accessToken = auth.accessToken.orEmpty(),
                refreshToken = auth.refreshToken,
                user = UserDto(id = userId, username = username)
            )
        )
    }

    private suspend fun usernameOf(userId: String): String? = with(supabase) {
        val response = http.get("$restUrl/profiles") {
            asService()
            parameter("select", "username")
            parameter("id", "eq.$userId")
        }
        if (!response.status.isSuccess()) error("Supabase ${response.status}: ${response.bodyAsText()}")
        response.body<List<UsernameRow>>().firstOrNull()?.username
    }

    /** Case-insensitive; `_` in a username is matched literally, not as a wildcard. */
    private suspend fun isUsernameTaken(username: String): Boolean = with(supabase) {
        val taken = count("profiles", "username" to "ilike.${escapeLike(username)}")
            ?: error("Couldn't check the username")
        taken > 0
    }

    private suspend fun getEmailForUsername(username: String): String? = with(supabase) {
        val response = http.post("$restUrl/rpc/email_for_username") {
            asService()
            contentType(ContentType.Application.Json)
            setBody(SupabaseRpcEmailRequest(p_username = username))
        }
        if (!response.status.isSuccess()) return null
        val text = response.bodyAsText().trim().removeSurrounding("\"")
        text.takeIf { it.isNotBlank() && it != "null" }
    }
}
