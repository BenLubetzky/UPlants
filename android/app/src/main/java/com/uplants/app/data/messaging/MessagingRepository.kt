package com.uplants.app.data.messaging

import com.uplants.app.data.model.Conversation
import com.uplants.app.data.model.Message
import com.uplants.app.data.model.MessagePage
import com.uplants.app.data.model.UnreadCount
import com.uplants.app.data.network.apiCall
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** messaging-service (port 8084). Every endpoint needs login. See services/messaging-service/README.md. */
interface MessagingApi {
    @POST("api/messages")
    suspend fun send(@Body body: NewMessageRequest): Message

    @GET("api/messages/conversations")
    suspend fun conversations(): List<Conversation>

    /** Newest first. */
    @GET("api/messages/with/{userId}")
    suspend fun thread(@Path("userId") userId: String, @Query("limit") limit: Int, @Query("offset") offset: Int): MessagePage

    @POST("api/messages/with/{userId}/read")
    suspend fun markRead(@Path("userId") userId: String)

    @GET("api/messages/unread-count")
    suspend fun unreadCount(): UnreadCount

    @DELETE("api/messages/{messageId}")
    suspend fun delete(@Path("messageId") messageId: String)
}

@Serializable
data class NewMessageRequest(
    @SerialName("recipient_id") val recipientId: String,
    val body: String? = null,
    @SerialName("shared_post_id") val sharedPostId: String? = null,
)

class MessagingRepository(private val api: MessagingApi) {

    /** Sharing a post = a message with [postId], with or without text. */
    suspend fun sharePost(recipientId: String, postId: String, text: String?): Result<Message> = apiCall {
        api.send(NewMessageRequest(recipientId, text?.trim()?.takeIf { it.isNotEmpty() }, postId))
    }

    suspend fun sendText(recipientId: String, text: String): Result<Message> =
        apiCall { api.send(NewMessageRequest(recipientId, text.trim())) }

    /** Inbox: one entry per person, latest first. */
    suspend fun conversations(): Result<List<Conversation>> = apiCall { api.conversations() }

    /** Messages with [userId], newest first. */
    suspend fun thread(userId: String, limit: Int, offset: Int): Result<MessagePage> = apiCall { api.thread(userId, limit, offset) }

    suspend fun markRead(userId: String): Result<Unit> = apiCall { api.markRead(userId) }

    suspend fun unreadCount(): Result<Int> = apiCall { api.unreadCount().unread }

    /** Only messages you sent can be deleted. */
    suspend fun delete(messageId: String): Result<Unit> = apiCall { api.delete(messageId) }
}
