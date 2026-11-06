package com.uplants.messaging.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- API responses ---

@Serializable
data class UserSummary(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

/** Enough of a shared post to draw a card; the full post comes from content-service. */
@Serializable
data class PostSummary(
    val id: String,
    val title: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val author: UserSummary? = null,
)

/** A message as stored, with sender, recipient and the shared post embedded. Also the API answer. */
@Serializable
data class MessageDto(
    val id: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("recipient_id") val recipientId: String,
    val body: String? = null,
    @SerialName("shared_post_id") val sharedPostId: String? = null,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    val sender: UserSummary? = null,
    val recipient: UserSummary? = null,
    /** Null if nothing was shared, or the post was deleted since. */
    @SerialName("shared_post") val sharedPost: PostSummary? = null,
)

@Serializable
data class MessagePage(
    val messages: List<MessageDto>,
    val limit: Int,
    val offset: Int,
)

/** One row of the inbox: the other person, the latest message and how many of theirs you haven't read. */
@Serializable
data class Conversation(
    val user: UserSummary,
    @SerialName("last_message") val lastMessage: MessageDto,
    @SerialName("unread_count") val unreadCount: Int,
)

@Serializable
data class UnreadCount(val unread: Int)

@Serializable
data class ErrorResponse(val error: String)

// --- API requests ---

/** A text, a shared post, or both. */
@Serializable
data class NewMessageRequest(
    @SerialName("recipient_id") val recipientId: String,
    val body: String? = null,
    @SerialName("shared_post_id") val sharedPostId: String? = null,
)

// --- Supabase rows ---

@Serializable
data class MessageInsert(
    @SerialName("sender_id") val senderId: String,
    @SerialName("recipient_id") val recipientId: String,
    val body: String?,
    @SerialName("shared_post_id") val sharedPostId: String?,
)

@Serializable
data class SenderRow(@SerialName("sender_id") val senderId: String)

@Serializable
data class ReadAtPatch(@SerialName("read_at") val readAt: String)

@Serializable
data class PostgrestError(val code: String? = null, val message: String? = null)

@Serializable
data class SupabaseUser(val id: String)
