package com.uplants.app.ui.dashboard

import com.uplants.app.data.model.ContentTypes
import com.uplants.app.data.model.DayCount
import com.uplants.app.data.model.Post
import com.uplants.app.data.model.PostStats
import com.uplants.app.data.notifications.activityText
import com.uplants.app.data.notifications.decodeCounts
import com.uplants.app.data.notifications.decodePostCounters
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardMathTest {

    private fun post(views: Int, likes: Int, comments: Int, ratingAvg: Double, ratingCount: Int) = Post(
        id = "p$views", contentType = ContentTypes.TEXT, title = "t", createdAt = "",
        viewCount = views, likeCount = likes, commentCount = comments, ratingAvg = ratingAvg, ratingCount = ratingCount,
    )

    @Test
    fun `totals add up and the average rating is weighted by number of ratings`() {
        val t = totalsOf(listOf(post(10, 2, 1, 5.0, 1), post(30, 4, 3, 2.0, 3)))
        assertEquals(2, t.posts)
        assertEquals(40, t.views)
        assertEquals(6, t.likes)
        assertEquals(4, t.comments)
        assertEquals(4, t.ratingCount)
        assertEquals((5.0 + 2.0 * 3) / 4, t.ratingAvg, 1e-9)
    }

    @Test
    fun `no ratings gives a zero average, not NaN`() {
        assertEquals(0.0, totalsOf(listOf(post(1, 0, 0, 0.0, 0))).ratingAvg, 0.0)
    }

    @Test
    fun `views per day and histograms are summed across posts`() {
        val a = PostStats("a", viewsByDay = listOf(DayCount("2026-11-01", 1), DayCount("2026-11-02", 2)), ratingHistogram = listOf(0, 0, 0, 1, 0, 2))
        val b = PostStats("b", viewsByDay = listOf(DayCount("2026-11-01", 3), DayCount("2026-11-02", 0)), ratingHistogram = listOf(1, 0, 0, 0, 0, 1))
        assertEquals(listOf(DayCount("2026-11-01", 4), DayCount("2026-11-02", 2)), sumViewsByDay(listOf(a, b)))
        assertEquals(listOf(1, 0, 0, 1, 0, 3), sumHistograms(listOf(a, b)))
    }

    @Test
    fun `notification texts and saved state round-trip`() {
        assertEquals("1 new comment and 3 new likes", activityText(1, 3))
        assertEquals("2 new comments", activityText(2, 0))
        assertEquals(mapOf("user-1" to 3), decodeCounts(setOf("user-1:3")))
        assertEquals(mapOf("p1" to (2 to 5)), decodePostCounters(setOf("p1:2:5", "broken")))
    }
}
