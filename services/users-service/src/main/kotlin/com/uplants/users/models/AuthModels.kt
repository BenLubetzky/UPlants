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
data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
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
data class SupabaseAuthSuccessResponse(
    @SerialName("access_token") val accessToken: String? = null,
    val user: SupabaseUserDto? = null
)

@Serializable
data class SupabaseUserDto(
    val id: String,
    val email: String? = null,
    @SerialName("user_metadata") val userMetadata: Map<String, String>? = null
)

@Serializable
data class SupabaseRpcEmailRequest(
    val p_username: String
)

@Serializable
data class ProfileDto(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String? = null
)
