package com.uplants.interactions

import com.uplants.interactions.models.DayCount
import com.uplants.interactions.service.Checked
import com.uplants.interactions.service.parsePage
import com.uplants.interactions.service.ratingHistogram
import com.uplants.interactions.service.validateComment
import com.uplants.interactions.service.validateReaction
import com.uplants.interactions.service.validateStars
import com.uplants.interactions.service.viewsByDay
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ValidationTest {

    @Test
    fun `comments are trimmed and limited`() {
        assertEquals("Nice fern!", assertIs<Checked.Valid<String>>(validateComment("  Nice fern!  ")).value)
        assertIs<Checked.Invalid>(validateComment("   "))
        assertIs<Checked.Invalid>(validateComment("a".repeat(1001)))
    }

    @Test
    fun `reaction is like or dislike`() {
        assertIs<Checked.Valid<Int>>(validateReaction(1))
        assertIs<Checked.Valid<Int>>(validateReaction(-1))
        assertIs<Checked.Invalid>(validateReaction(0))
        assertIs<Checked.Invalid>(validateReaction(2))
    }

    @Test
    fun `stars go from 0 to 5`() {
        assertIs<Checked.Valid<Int>>(validateStars(0))
        assertIs<Checked.Valid<Int>>(validateStars(5))
        assertIs<Checked.Invalid>(validateStars(6))
        assertIs<Checked.Invalid>(validateStars(-1))
    }

    @Test
    fun `page defaults and limits`() {
        assertEquals(20 to 0, assertIs<Checked.Valid<Pair<Int, Int>>>(parsePage(null, null)).value)
        assertIs<Checked.Invalid>(parsePage("0", null))
        assertIs<Checked.Invalid>(parsePage("10", "-1"))
    }

    @Test
    fun `histogram counts each star value`() {
        assertEquals(listOf(1, 0, 0, 2, 0, 1), ratingHistogram(listOf(3, 5, 0, 3)))
        assertEquals(List(6) { 0 }, ratingHistogram(emptyList()))
    }

    @Test
    fun `views are grouped per UTC day with empty days filled in`() {
        val today = LocalDate.parse("2026-11-03")
        val result = viewsByDay(
            listOf("2026-11-03T10:00:00+00:00", "2026-11-03T23:30:00+00:00", "2026-11-02T00:30:00+01:00", "garbage"),
            today,
            days = 3,
        )
        assertEquals(
            listOf(DayCount("2026-11-01", 1), DayCount("2026-11-02", 0), DayCount("2026-11-03", 2)),
            result,
        )
    }
}
