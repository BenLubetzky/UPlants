package com.uplants.app.data.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

/** Auth endpoints of users-service (port 8080). See services/users-service/README.md. */
interface AuthApi {
    @POST("api/users/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("api/users/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    /** Blocking on purpose: called from OkHttp's authenticator thread when a token expires. */
    @POST("api/users/refresh")
    fun refresh(@Body body: RefreshRequest): Call<AuthResponse>
}

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class RegisterRequest(val username: String, val email: String, val password: String)

@Serializable
data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val user: UserDto,
)

@Serializable
data class UserDto(val id: String, val username: String)
