package com.uplants.messaging.service

import com.uplants.messaging.models.Conversation
import com.uplants.messaging.models.MessageDto
import com.uplants.messaging.models.MessageInsert
import com.uplants.messaging.models.NewMessageRequest
import java.time.Instant
import java.time.OffsetDateTime

/** Input checks and inbox grouping with no I/O, so they're easy to unit test. */

sealed interface Checked<out T> {
    data class Valid<T>(val value: T) : Checked<T>
    data class Invalid(val message: String) : Checked<Nothing>
}

private val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

fun isUuid(value: String) = UUID.matches(value)

/** Reads `limit` / `offset` (limit 1-50, default 20). */
fun parsePage(limitParam: String?, offsetParam: String?): Checked<Pair<Int, Int>> {
    val limit = limitParam?.let { it.toIntOrNull()?.takeIf { n -> n in 1..50 } ?: return Checked.Invalid("limit must be 1-50") } ?: 20
    val offset = offsetParam?.let { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return Checked.Invalid("offset must be 0 or more") } ?: 0
    return Checked.Valid(limit to offset)
}

const val MAX_BODY = 2000 // same as the database check

/** Checks a new message from [senderId] and turns it into the row to insert (body trimmed, blank -> none). */
fun validateNewMessage(senderId: String, request: NewMessageRequest): Checked<MessageInsert> {
    if (!isUuid(request.recipientId)) return Checked.Invalid("recipient_id must be a user id")
    if (request.recipientId == senderId) return Checked.Invalid("You can't message yourself")
    val body = request.body?.trim()?.ifEmpty { null }
    if (body != null && body.length > MAX_BODY) return Checked.Invalid("body is too long (max $MAX_BODY)")
    val post = request.sharedPostId
    if (post != null && !isUuid(post)) return Checked.Invalid("shared_post_id must be a post id")
    if (body == null && post == null) return Checked.Invalid("Send a body, a shared_post_id, or both")
    return Checked.Valid(MessageInsert(senderId, request.recipientId, body, post))
}

/** The PostgREST `or=(...)` filter for the messages between two users, in both directions. */
fun threadFilter(me: String, other: String) =
    "(and(sender_id.eq.$me,recipient_id.eq.$other),and(sender_id.eq.$other,recipient_id.eq.$me))"

/**
 * Groups [messages] (any order) into one conversation per person, newest conversation first.
 * Messages without the other person's profile embedded are skipped.
 */
fun groupConversations(me: String, messages: List<MessageDto>): List<Conversation> =
    messages
        .groupBy { if (it.senderId == me) it.recipientId else it.senderId }
        .mapNotNull { (_, thread) ->
            val latest = thread.maxBy { instantOf(it.createdAt) }
            val other = if (latest.senderId == me) latest.recipient else latest.sender
            other?.let {
                Conversation(
                    user = it,
                    lastMessage = latest,
                    unreadCount = thread.count { m -> m.recipientId == me && m.readAt == null },
                )
            }
        }
        .sortedByDescending { instantOf(it.lastMessage.createdAt) }

private fun instantOf(timestamp: String): Instant =
    runCatching { OffsetDateTime.parse(timestamp).toInstant() }.getOrDefault(Instant.EPOCH)

/** Messages read to build the inbox (the newest ones). */
const val INBOX_MESSAGES = 500
