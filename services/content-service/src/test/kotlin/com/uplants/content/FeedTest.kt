package com.uplants.content

import com.uplants.content.models.AuthorDto
import com.uplants.content.models.CategoryDto
import com.uplants.content.models.PostDto
import com.uplants.content.service.FeedSignals
import com.uplants.content.service.feedCandidateFilter
import com.uplants.content.service.feedScore
import com.uplants.content.service.rankFeed
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedTest {

    private val now = Instant.parse("2026-11-06T12:00:00Z")
    private val signals = FeedSignals(followedAuthors = setOf("friend"), interestCategories = setOf(1))

    private fun post(
        id: String,
        author: String = "stranger",
        category: Int = 9,
        rating: Double = 0.0,
        interactions: Int = 0,
        createdAt: String = "2026-11-06T11:00:00Z",
    ) = PostDto(
        id = id,
        author = AuthorDto(author, author),
        category = CategoryDto(category, "c$category", "c$category"),
        contentType = "photo",
        source = "upload",
        title = id,
        ratingAvg = rating,
        interactionCount = interactions,
        createdAt = createdAt,
    )

    @Test
    fun `followed authors come before interests, interests before strangers`() {
        val ranked = rankFeed(listOf(post("stranger"), post("interest", category = 1), post("friend", author = "friend")), signals, now)
        assertEquals(listOf("friend", "interest", "stranger"), ranked.map { it.id })
    }

    @Test
    fun `a great post by a stranger can beat a plain post in an interest category`() {
        val great = post("great", rating = 5.0, interactions = 200)
        val plain = post("plain", category = 1)
        assertTrue(feedScore(great, signals, now) > feedScore(plain, signals, now))
    }

    @Test
    fun `older posts score lower, but only up to 30 days`() {
        val fresh = feedScore(post("a"), signals, now)
        val weekOld = feedScore(post("b", createdAt = "2026-10-30T11:00:00Z"), signals, now)
        val monthOld = feedScore(post("c", createdAt = "2026-10-07T11:00:00Z"), signals, now)
        val yearOld = feedScore(post("d", createdAt = "2025-11-06T11:00:00Z"), signals, now)
        assertTrue(fresh > weekOld && weekOld > monthOld)
        assertEquals(monthOld, yearOld, 0.01)
    }

    @Test
    fun `duplicates are dropped and ties go to the newest`() {
        val ranked = rankFeed(
            listOf(post("old", createdAt = "2026-11-06T10:00:00Z"), post("new", createdAt = "2026-11-06T10:00:30Z"), post("old", createdAt = "2026-11-06T10:00:00Z")),
            signals,
            now,
        )
        assertEquals(listOf("new", "old"), ranked.map { it.id })
    }

    @Test
    fun `candidate filter matches followed authors or interest categories`() {
        assertEquals("(author_id.in.(a,b),category_id.in.(1,2))", feedCandidateFilter(FeedSignals(setOf("a", "b"), setOf(1, 2))))
        assertEquals("(category_id.in.(3))", feedCandidateFilter(FeedSignals(emptySet(), setOf(3))))
        assertNull(feedCandidateFilter(FeedSignals(emptySet(), emptySet())))
    }
}
