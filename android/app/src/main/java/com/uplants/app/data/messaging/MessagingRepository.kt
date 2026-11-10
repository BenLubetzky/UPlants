package com.uplants.app.data.messaging

import com.uplants.app.data.model.Message
import com.uplants.app.data.network.apiCall
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/** messaging-service (port 8084). See services/messaging-service/README.md. */
interface MessagingApi {
    @POST("api/messages")
    suspend fun send(@Body body: NewMessageRequest): Message
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
}
