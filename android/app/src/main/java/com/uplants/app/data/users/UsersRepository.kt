package com.uplants.app.data.users

import com.uplants.app.data.model.UserPage
import com.uplants.app.data.network.apiCall
import retrofit2.http.GET
import retrofit2.http.Query

/** People endpoints of users-service (port 8080). See services/users-service/README.md. */
interface UsersApi {
    @GET("api/users/search")
    suspend fun search(@Query("q") query: String, @Query("limit") limit: Int, @Query("offset") offset: Int): UserPage
}

class UsersRepository(private val api: UsersApi) {

    /** Users whose username or display name contains [query] (1-50 characters). */
    suspend fun search(query: String, limit: Int = 20, offset: Int = 0): Result<UserPage> =
        apiCall { api.search(query.trim().take(50), limit, offset) }
}
