package com.uplants.app.ui.post

import com.uplants.app.data.model.ContentTypes
import com.uplants.app.data.model.Post
import com.uplants.app.ui.common.compactCount
import org.junit.Assert.assertEquals
import org.junit.Test

class ReactionCountersTest {

    private val post = Post(id = "1", contentType = ContentTypes.TEXT, title = "t", createdAt = "", likeCount = 5, dislikeCount = 2)

    @Test
    fun `like from nothing adds a like`() {
        val p = post.withReaction(old = null, new = 1)
        assertEquals(6, p.likeCount)
        assertEquals(2, p.dislikeCount)
    }

    @Test
    fun `switching like to dislike moves the count`() {
        val p = post.withReaction(old = 1, new = -1)
        assertEquals(4, p.likeCount)
        assertEquals(3, p.dislikeCount)
    }

    @Test
    fun `removing a reaction never goes below zero`() {
        val p = post.copy(likeCount = 0).withReaction(old = 1, new = null)
        assertEquals(0, p.likeCount)
    }

    @Test
    fun `compact counts`() {
        assertEquals("999", compactCount(999))
        assertEquals("1K", compactCount(1_000))
        assertEquals("1.2K", compactCount(1_234))
        assertEquals("3.4M", compactCount(3_400_000))
    }
}
