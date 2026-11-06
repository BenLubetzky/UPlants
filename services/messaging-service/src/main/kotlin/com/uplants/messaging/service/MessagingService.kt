package com.uplants.messaging.service

import com.uplants.messaging.models.Conversation
import com.uplants.messaging.models.MessageDto
import com.uplants.messaging.models.MessageInsert
import com.uplants.messaging.models.MessagePage
import com.uplants.messaging.models.PostgrestError
import com.uplants.messaging.models.ReadAtPatch
import com.uplants.messaging.models.SenderRow
import com.uplants.messaging.models.UnreadCount
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import org.slf4j.LoggerFactory
import java.time.Instant

sealed interface ServiceResult<out T> {
    data class Ok<T>(val value: T, val status: HttpStatusCode = HttpStatusCode.OK) : ServiceResult<T>
    data class Fail(val status: HttpStatusCode, val message: String) : ServiceResult<Nothing>
}

private val USER_NOT_FOUND = ServiceResult.Fail(HttpStatusCode.NotFound, "User not found")

/**
 * Private messages between users. Sharing a post = a message with `shared_post_id`.
 * Owns `messages`. The database keeps `posts.share_count` and creates the `message` / `share` notifications.
 */
class MessagingService(private val supabase: SupabaseClient) {

    private val log = LoggerFactory.getLogger(MessagingService::class.java)

    private companion object {
        const val USER = "id,username,display_name,avatar_url"
        const val MESSAGE_SELECT = "id,sender_id,recipient_id,body,shared_post_id,read_at,created_at," +
            "sender:profiles!messages_sender_id_fkey($USER)," +
            "recipient:profiles!messages_recipient_id_fkey($USER)," +
            "shared_post:posts!messages_shared_post_id_fkey(id,title,content_type,media_url,thumbnail_url," +
            "author:profiles!posts_author_id_fkey($USER))"
        const val SINGLE_OBJECT = "application/vnd.pgrst.object+json"
    }

    /** `message` must be validated (see validateNewMessage). */
    suspend fun send(message: MessageInsert): ServiceResult<MessageDto> = with(supabase) {
        val response = http.post("$restUrl/messages") {
            asService()
            header("Prefer", "return=representation")
            header("Accept", SINGLE_OBJECT)
            parameter("select", MESSAGE_SELECT)
            contentType(ContentType.Application.Json)
            setBody(message)
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        ServiceResult.Ok(response.body(), HttpStatusCode.Created)
    }

    /** One entry per person you've talked to, latest conversation first. */
    suspend fun conversations(userId: String): ServiceResult<List<Conversation>> = with(supabase) {
        val response = http.get("$restUrl/messages") {
            asService()
            parameter("select", MESSAGE_SELECT)
            parameter("or", "(sender_id.eq.$userId,recipient_id.eq.$userId)")
            parameter("order", "created_at.desc")
            parameter("limit", INBOX_MESSAGES)
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        ServiceResult.Ok(groupConversations(userId, response.body()))
    }

    /** Messages between you and [otherId], newest first. */
    suspend fun thread(userId: String, otherId: String, limit: Int, offset: Int): ServiceResult<MessagePage> = with(supabase) {
        if (!isUuid(otherId)) return USER_NOT_FOUND
        val response = http.get("$restUrl/messages") {
            asService()
            parameter("select", MESSAGE_SELECT)
            parameter("or", threadFilter(userId, otherId))
            parameter("order", "created_at.desc")
            parameter("limit", limit)
            parameter("offset", offset)
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        ServiceResult.Ok(MessagePage(response.body(), limit, offset))
    }

    /** Marks everything [otherId] sent you as read. */
    suspend fun markRead(userId: String, otherId: String): ServiceResult<Unit> = with(supabase) {
        if (!isUuid(otherId)) return USER_NOT_FOUND
        val response = http.patch("$restUrl/messages") {
            asService()
            header("Prefer", "return=minimal")
            parameter("sender_id", "eq.$otherId")
            parameter("recipient_id", "eq.$userId")
            parameter("read_at", "is.null")
            contentType(ContentType.Application.Json)
            setBody(ReadAtPatch(Instant.now().toString()))
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        ServiceResult.Ok(Unit, HttpStatusCode.NoContent)
    }

    suspend fun unreadCount(userId: String): ServiceResult<UnreadCount> = with(supabase) {
        val unread = count("messages", "recipient_id" to "eq.$userId", "read_at" to "is.null")
            ?: return ServiceResult.Fail(HttpStatusCode.BadGateway, "Database error")
        ServiceResult.Ok(UnreadCount(unread))
    }

    /** Only the sender can delete a message (a shared post's share_count goes down again). */
    suspend fun delete(userId: String, messageId: String): ServiceResult<Unit> = with(supabase) {
        val notFound = ServiceResult.Fail(HttpStatusCode.NotFound, "Message not found")
        if (!isUuid(messageId)) return notFound
        val lookup = http.get("$restUrl/messages") {
            asService()
            parameter("select", "sender_id")
            parameter("id", "eq.$messageId")
        }
        if (!lookup.status.isSuccess()) return upstreamError(lookup)
        val owner = lookup.body<List<SenderRow>>().firstOrNull() ?: return notFound
        if (owner.senderId != userId) return ServiceResult.Fail(HttpStatusCode.Forbidden, "You can only delete messages you sent")

        val response = http.delete("$restUrl/messages") {
            asService()
            parameter("id", "eq.$messageId")
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        ServiceResult.Ok(Unit, HttpStatusCode.NoContent)
    }

    /** Turns a Supabase error into something the app can act on. */
    private suspend fun upstreamError(response: HttpResponse): ServiceResult.Fail {
        val text = response.bodyAsText()
        val error = runCatching { supabase.json.decodeFromString<PostgrestError>(text) }.getOrNull()
        return when (error?.code) {
            "23503" -> ServiceResult.Fail(HttpStatusCode.NotFound, "User or post not found")
            "23514", "22P02", "22001" -> ServiceResult.Fail(HttpStatusCode.BadRequest, "Invalid message")
            else -> {
                log.error("Supabase error {}: {}", response.status, text)
                ServiceResult.Fail(HttpStatusCode.BadGateway, "Database error")
            }
        }
    }
}
