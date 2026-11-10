package com.uplants.app.data.favorites

import com.uplants.app.data.model.FavoriteList
import com.uplants.app.data.model.ListMembership
import com.uplants.app.data.network.apiCall
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** favorites-service (port 8083). See services/favorites-service/README.md. */
interface FavoritesApi {
    @POST("api/favorites/lists")
    suspend fun createList(@Body body: NewListRequest): FavoriteList

    @PUT("api/favorites/lists/{listId}/posts/{postId}")
    suspend fun addPost(@Path("listId") listId: String, @Path("postId") postId: String)

    @DELETE("api/favorites/lists/{listId}/posts/{postId}")
    suspend fun removePost(@Path("listId") listId: String, @Path("postId") postId: String)

    @GET("api/favorites/posts/{postId}/lists")
    suspend fun listsForPost(@Path("postId") postId: String): List<ListMembership>
}

@Serializable
data class NewListRequest(val name: String, @SerialName("is_public") val isPublic: Boolean = false)

class FavoritesRepository(private val api: FavoritesApi) {

    /** All my lists, each saying whether [postId] is in it (for the "Save to…" sheet). */
    suspend fun listsForPost(postId: String): Result<List<ListMembership>> = apiCall { api.listsForPost(postId) }

    suspend fun setSaved(listId: String, postId: String, saved: Boolean): Result<Unit> = apiCall {
        if (saved) api.addPost(listId, postId) else api.removePost(listId, postId)
    }

    suspend fun createList(name: String, isPublic: Boolean = false): Result<FavoriteList> =
        apiCall { api.createList(NewListRequest(name.trim(), isPublic)) }
}
