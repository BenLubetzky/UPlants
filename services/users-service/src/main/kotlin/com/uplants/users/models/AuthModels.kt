package com.uplants.users.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Client API Request & Response DTOs ---

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String
)

@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
    /** Send it to `POST /api/users/refresh` to get a new access token (they expire after about 1 hour). */
    @SerialName("refresh_token") val refreshToken: String? = null,
    val user: UserDto
)

@Serializable
data class UserDto(
    val id: String,
    val username: String
)

@Serializable
data class ErrorResponse(
    val error: String
)

// --- Supabase Internal Auth & REST DTOs ---

@Serializable
data class SupabaseSignUpRequest(
    val email: String,
    val password: String,
    val data: Map<String, String>
)

@Serializable
data class SupabaseTokenRequest(
    val email: String,
    val password: String
)

@Serializable
data class SupabaseRefreshRequest(
    @SerialName("refresh_token") val refreshToken: String
)

@Serializable
data class SupabaseAuthSuccessResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val user: SupabaseUserDto? = null
)

@Serializable
data class SupabaseUserDto(
    val id: String,
    val email: String? = null,
)

/** Answer of `GET /auth/v1/user`, used to check access tokens. */
@Serializable
data class SupabaseUser(val id: String)

@Serializable
data class SupabaseRpcEmailRequest(
    val p_username: String
)

@Serializable
data class UsernameRow(val username: String)

@Serializable
data class PostgrestError(val code: String? = null, val message: String? = null)
