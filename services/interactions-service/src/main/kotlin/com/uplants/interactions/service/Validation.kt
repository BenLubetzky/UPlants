package com.uplants.interactions.service

import com.uplants.interactions.models.DayCount
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Input checks and stats math with no I/O, so they're easy to unit test. */

sealed interface Checked<out T> {
    data class Valid<T>(val value: T) : Checked<T>
    data class Invalid(val message: String) : Checked<Nothing>
}

private val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

fun isUuid(value: String) = UUID.matches(value)

fun validateComment(body: String): Checked<String> {
    val text = body.trim()
    if (text.isEmpty()) return Checked.Invalid("comment can't be empty")
    if (text.length > 1000) return Checked.Invalid("comment is too long (max 1000)")
    return Checked.Valid(text)
}

fun validateReaction(value: Int): Checked<Int> =
    if (value == 1 || value == -1) Checked.Valid(value) else Checked.Invalid("value must be 1 (like) or -1 (dislike)")

fun validateStars(stars: Int): Checked<Int> =
    if (stars in 0..5) Checked.Valid(stars) else Checked.Invalid("stars must be 0-5")

/** Reads `limit` / `offset` (limit 1-50, default 20). */
fun parsePage(limitParam: String?, offsetParam: String?): Checked<Pair<Int, Int>> {
    val limit = limitParam?.let { it.toIntOrNull()?.takeIf { n -> n in 1..50 } ?: return Checked.Invalid("limit must be 1-50") } ?: 20
    val offset = offsetParam?.let { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return Checked.Invalid("offset must be 0 or more") } ?: 0
    return Checked.Valid(limit to offset)
}

/** How many ratings gave 0, 1, ... 5 stars. */
fun ratingHistogram(stars: List<Int>): List<Int> {
    val counts = IntArray(6)
    stars.filter { it in 0..5 }.forEach { counts[it]++ }
    return counts.toList()
}

/** Views per day (UTC) for the last [days] days, oldest first, including days with 0. */
fun viewsByDay(viewedAt: List<String>, today: LocalDate, days: Int = STATS_DAYS): List<DayCount> {
    val perDay = viewedAt
        .mapNotNull { runCatching { OffsetDateTime.parse(it).withOffsetSameInstant(ZoneOffset.UTC).toLocalDate() }.getOrNull() }
        .groupingBy { it }
        .eachCount()
    return (days - 1 downTo 0).map { back ->
        val day = today.minusDays(back.toLong())
        DayCount(day.toString(), perDay[day] ?: 0)
    }
}

const val STATS_DAYS = 30

/** Same viewer within this window counts as one view. */
const val VIEW_DEDUP_MINUTES = 30L
