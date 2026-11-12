package com.uplants.app.data.users

import com.uplants.app.data.model.Category
import com.uplants.app.data.model.Profile
import com.uplants.app.data.model.UserPage
import com.uplants.app.data.network.apiCall
import com.uplants.app.data.offline.OfflineCache
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** People endpoints of users-service (port 8080). See services/users-service/README.md. */
interface UsersApi {
    @GET("api/users/search")
    suspend fun search(@Query("q") query: String, @Query("limit") limit: Int, @Query("offset") offset: Int): UserPage

    @GET("api/users/{id}")
    suspend fun profile(@Path("id") id: String): Profile

    @GET("api/users/me")
    suspend fun me(): Profile

    @PATCH("api/users/me")
    suspend fun updateMe(@Body body: UpdateProfileRequest): Profile

    @GET("api/users/me/interests")
    suspend fun interests(): List<Category>

    @PUT("api/users/me/interests")
    suspend fun setInterests(@Body body: InterestsRequest): List<Category>

    @PUT("api/users/{id}/follow")
    suspend fun follow(@Path("id") id: String)

    @DELETE("api/users/{id}/follow")
    suspend fun unfollow(@Path("id") id: String)

    @GET("api/users/{id}/followers")
    suspend fun followers(@Path("id") id: String, @Query("limit") limit: Int, @Query("offset") offset: Int): UserPage

    @GET("api/users/{id}/following")
    suspend fun following(@Path("id") id: String, @Query("limit") limit: Int, @Query("offset") offset: Int): UserPage
}

/** Fields left out don't change; "" clears them. */
@Serializable
data class UpdateProfileRequest(
    @SerialName("display_name") val displayName: String? = null,
    val bio: String? = null,
)

@Serializable
data class InterestsRequest(@SerialName("category_ids") val categoryIds: List<Int>)

class UsersRepository(private val api: UsersApi, private val cache: OfflineCache) {

    /** Users whose username or display name contains [query] (1-50 characters). */
    suspend fun search(query: String, limit: Int = 20, offset: Int = 0): Result<UserPage> =
        apiCall { api.search(query.trim().take(50), limit, offset) }

    /** With a token, [Profile.isFollowing] says whether the logged-in user follows them. */
    suspend fun profile(id: String): Result<Profile> =
        cache.withFallback("profile|$id", Profile.serializer()) { apiCall { api.profile(id) } }

    suspend fun updateMe(displayName: String, bio: String): Result<Profile> =
        apiCall { api.updateMe(UpdateProfileRequest(displayName.trim(), bio.trim())) }

    suspend fun interests(): Result<List<Category>> = apiCall { api.interests() }

    suspend fun setInterests(categoryIds: List<Int>): Result<List<Category>> =
        apiCall { api.setInterests(InterestsRequest(categoryIds)) }

    suspend fun setFollowing(id: String, follow: Boolean): Result<Unit> =
        apiCall { if (follow) api.follow(id) else api.unfollow(id) }

    suspend fun followers(id: String, limit: Int, offset: Int): Result<UserPage> = apiCall { api.followers(id, limit, offset) }

    suspend fun following(id: String, limit: Int, offset: Int): Result<UserPage> = apiCall { api.following(id, limit, offset) }
}
