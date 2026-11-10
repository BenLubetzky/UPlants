package com.uplants.app.data.interactions

import com.uplants.app.data.model.Comment
import com.uplants.app.data.model.CommentPage
import com.uplants.app.data.model.MyInteractions
import com.uplants.app.data.network.apiCall
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** interactions-service (port 8082). See services/interactions-service/README.md. */
interface InteractionsApi {
    @GET("api/interactions/posts/{postId}/comments")
    suspend fun comments(@Path("postId") postId: String, @Query("limit") limit: Int, @Query("offset") offset: Int): CommentPage

    @POST("api/interactions/posts/{postId}/comments")
    suspend fun addComment(@Path("postId") postId: String, @Body body: NewComment): Comment

    @DELETE("api/interactions/comments/{commentId}")
    suspend fun deleteComment(@Path("commentId") commentId: String)

    @PUT("api/interactions/posts/{postId}/reaction")
    suspend fun react(@Path("postId") postId: String, @Body body: ReactionRequest)

    @DELETE("api/interactions/posts/{postId}/reaction")
    suspend fun removeReaction(@Path("postId") postId: String)

    @PUT("api/interactions/posts/{postId}/rating")
    suspend fun rate(@Path("postId") postId: String, @Body body: RatingRequest)

    @DELETE("api/interactions/posts/{postId}/rating")
    suspend fun removeRating(@Path("postId") postId: String)

    @GET("api/interactions/posts/{postId}/me")
    suspend fun mine(@Path("postId") postId: String): MyInteractions

    @POST("api/interactions/posts/{postId}/views")
    suspend fun recordView(@Path("postId") postId: String)
}

@Serializable
data class NewComment(val body: String)

@Serializable
data class ReactionRequest(val value: Int)

@Serializable
data class RatingRequest(val stars: Int)

class InteractionsRepository(private val api: InteractionsApi) {

    suspend fun comments(postId: String, limit: Int, offset: Int): Result<CommentPage> =
        apiCall { api.comments(postId, limit, offset) }

    suspend fun addComment(postId: String, body: String): Result<Comment> =
        apiCall { api.addComment(postId, NewComment(body.trim())) }

    suspend fun deleteComment(commentId: String): Result<Unit> = apiCall { api.deleteComment(commentId) }

    /** [value]: 1 like, -1 dislike, null to remove. */
    suspend fun setReaction(postId: String, value: Int?): Result<Unit> = apiCall {
        if (value == null) api.removeReaction(postId) else api.react(postId, ReactionRequest(value))
    }

    /** [stars]: 0-5, null to remove. */
    suspend fun setRating(postId: String, stars: Int?): Result<Unit> = apiCall {
        if (stars == null) api.removeRating(postId) else api.rate(postId, RatingRequest(stars))
    }

    suspend fun mine(postId: String): Result<MyInteractions> = apiCall { api.mine(postId) }

    suspend fun recordView(postId: String): Result<Unit> = apiCall { api.recordView(postId) }
}
