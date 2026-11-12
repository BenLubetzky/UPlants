package com.uplants.app.data.favorites

import com.uplants.app.data.model.FavoriteList
import com.uplants.app.data.model.FavoriteListDetail
import com.uplants.app.data.model.ListMembership
import com.uplants.app.data.network.apiCall
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** favorites-service (port 8083). See services/favorites-service/README.md. */
interface FavoritesApi {
    @GET("api/favorites/lists")
    suspend fun myLists(): List<FavoriteList>

    @POST("api/favorites/lists")
    suspend fun createList(@Body body: NewListRequest): FavoriteList

    @GET("api/favorites/lists/{listId}")
    suspend fun list(@Path("listId") listId: String, @Query("limit") limit: Int, @Query("offset") offset: Int): FavoriteListDetail

    @PATCH("api/favorites/lists/{listId}")
    suspend fun updateList(@Path("listId") listId: String, @Body body: UpdateListRequest): FavoriteList

    @DELETE("api/favorites/lists/{listId}")
    suspend fun deleteList(@Path("listId") listId: String)

    @PUT("api/favorites/lists/{listId}/posts/{postId}")
    suspend fun addPost(@Path("listId") listId: String, @Path("postId") postId: String)

    @DELETE("api/favorites/lists/{listId}/posts/{postId}")
    suspend fun removePost(@Path("listId") listId: String, @Path("postId") postId: String)

    @GET("api/favorites/posts/{postId}/lists")
    suspend fun listsForPost(@Path("postId") postId: String): List<ListMembership>

    @GET("api/favorites/users/{userId}/lists")
    suspend fun userLists(@Path("userId") userId: String): List<FavoriteList>
}

@Serializable
data class NewListRequest(val name: String, @SerialName("is_public") val isPublic: Boolean = false)

/** Fields left out don't change. */
@Serializable
data class UpdateListRequest(val name: String? = null, @SerialName("is_public") val isPublic: Boolean? = null)

class FavoritesRepository(private val api: FavoritesApi) {

    /** My lists, private ones too, newest first. */
    suspend fun myLists(): Result<List<FavoriteList>> = apiCall { api.myLists() }

    /** Someone's public lists (all of them if it's me). */
    suspend fun userLists(userId: String): Result<List<FavoriteList>> = apiCall { api.userLists(userId) }

    /** A list with a page of its posts, newest saved first. Someone else's private list is a 404. */
    suspend fun list(listId: String, limit: Int, offset: Int): Result<FavoriteListDetail> = apiCall { api.list(listId, limit, offset) }

    suspend fun updateList(listId: String, name: String? = null, isPublic: Boolean? = null): Result<FavoriteList> =
        apiCall { api.updateList(listId, UpdateListRequest(name?.trim(), isPublic)) }

    suspend fun deleteList(listId: String): Result<Unit> = apiCall { api.deleteList(listId) }

    /** All my lists, each saying whether [postId] is in it (for the "Save to…" sheet). */
    suspend fun listsForPost(postId: String): Result<List<ListMembership>> = apiCall { api.listsForPost(postId) }

    suspend fun setSaved(listId: String, postId: String, saved: Boolean): Result<Unit> = apiCall {
        if (saved) api.addPost(listId, postId) else api.removePost(listId, postId)
    }

    suspend fun createList(name: String, isPublic: Boolean = false): Result<FavoriteList> =
        apiCall { api.createList(NewListRequest(name.trim(), isPublic)) }
}
