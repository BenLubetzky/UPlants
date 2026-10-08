package com.uplants.app.data.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * REST contract the app expects from the backend's auth/users service.
 * Paths and JSON field names are placeholders: adjust them here once the backend team
 * finalises their API — nothing else in the app needs to change.
 */
interface AuthApi {
    @POST("api/users/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("api/users/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse
}

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class RegisterRequest(val username: String, val email: String, val password: String)

@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
    val user: UserDto,
)

@Serializable
data class UserDto(val id: Long, val username: String)
