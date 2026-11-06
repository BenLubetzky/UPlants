package com.uplants.messaging

import com.uplants.messaging.models.MessageDto
import com.uplants.messaging.models.MessageInsert
import com.uplants.messaging.models.NewMessageRequest
import com.uplants.messaging.models.UserSummary
import com.uplants.messaging.service.Checked
import com.uplants.messaging.service.groupConversations
import com.uplants.messaging.service.threadFilter
import com.uplants.messaging.service.validateNewMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ValidationTest {

    private val me = "11111111-1111-1111-1111-111111111111"
    private val ana = "22222222-2222-2222-2222-222222222222"
    private val ben = "33333333-3333-3333-3333-333333333333"
    private val post = "44444444-4444-4444-4444-444444444444"

    @Test
    fun `a message needs a text or a shared post`() {
        assertEquals(
            MessageInsert(me, ana, "Hi!", null),
            assertIs<Checked.Valid<MessageInsert>>(validateNewMessage(me, NewMessageRequest(ana, "  Hi! "))).value,
        )
        assertEquals(
            MessageInsert(me, ana, null, post),
            assertIs<Checked.Valid<MessageInsert>>(validateNewMessage(me, NewMessageRequest(ana, "   ", post))).value,
        )
        assertIs<Checked.Invalid>(validateNewMessage(me, NewMessageRequest(ana)))
        assertIs<Checked.Invalid>(validateNewMessage(me, NewMessageRequest(ana, "   ")))
    }

    @Test
    fun `bad recipients, posts and long texts are refused`() {
        assertIs<Checked.Invalid>(validateNewMessage(me, NewMessageRequest(me, "talking to myself")))
        assertIs<Checked.Invalid>(validateNewMessage(me, NewMessageRequest("ana", "Hi")))
        assertIs<Checked.Invalid>(validateNewMessage(me, NewMessageRequest(ana, sharedPostId = "post-1")))
        assertIs<Checked.Invalid>(validateNewMessage(me, NewMessageRequest(ana, "a".repeat(2001))))
        assertIs<Checked.Valid<MessageInsert>>(validateNewMessage(me, NewMessageRequest(ana, "a".repeat(2000))))
    }

    @Test
    fun `thread filter covers both directions`() {
        assertEquals("(and(sender_id.eq.a,recipient_id.eq.b),and(sender_id.eq.b,recipient_id.eq.a))", threadFilter("a", "b"))
    }

    private fun msg(id: String, from: String, to: String, at: String, read: Boolean = false) = MessageDto(
        id = id, senderId = from, recipientId = to, body = id, createdAt = at,
        readAt = if (read) at else null,
        sender = UserSummary(from, "u$from"), recipient = UserSummary(to, "u$to"),
    )

    @Test
    fun `inbox has one row per person, latest first, with unread counts`() {
        val inbox = groupConversations(
            me,
            listOf(
                msg("a1", ana, me, "2026-11-06T10:00:00+00:00", read = true),
                msg("a2", me, ana, "2026-11-06T10:05:00+00:00"),
                msg("b1", ben, me, "2026-11-06T09:00:00+00:00"),
                msg("b2", ben, me, "2026-11-06T11:00:00.5+00:00"),
                msg("b3", me, ben, "2026-11-06T11:00:00.25+00:00"), // earlier than b2 despite the longer text
            ),
        )
        assertEquals(listOf(ben, ana), inbox.map { it.user.id })
        assertEquals("b2", inbox[0].lastMessage.id)
        assertEquals(2, inbox[0].unreadCount)   // b1 and b2: sent to me, not read
        assertEquals("a2", inbox[1].lastMessage.id)
        assertEquals(0, inbox[1].unreadCount)   // a1 is read; a2 is mine
    }
}
