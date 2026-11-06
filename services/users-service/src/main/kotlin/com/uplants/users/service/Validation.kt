package com.uplants.users.service

import com.uplants.users.models.UpdateProfileRequest

/** Input checks with no I/O, so they're easy to unit test. */

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

/**
 * Escapes text for a SQL LIKE pattern so `%` and `_` match themselves instead of acting as wildcards.
 * `*` is removed: PostgREST turns it into `%`.
 */
fun escapeLike(text: String): String =
    text.replace("*", "")
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

/** Wraps a value in double quotes for a PostgREST `or=(...)` filter, so commas and parentheses are safe. */
fun quoteForFilter(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

/** The `or=(...)` filter for searching users by part of their username or display name. */
fun searchFilter(query: String): String {
    val pattern = quoteForFilter("*${escapeLike(query)}*")
    return "(username.ilike.$pattern,display_name.ilike.$pattern)"
}

fun validateSearch(q: String?): Checked<String> {
    val text = q?.trim().orEmpty()
    if (text.isEmpty()) return Checked.Invalid("q is required")
    if (text.length > 50) return Checked.Invalid("q is too long (max 50)")
    if (text.replace("*", "").isEmpty()) return Checked.Invalid("q must contain letters or numbers")
    return Checked.Valid(text)
}

const val MAX_DISPLAY_NAME = 50
const val MAX_BIO = 500

/** Turns the request into the columns to update: trimmed, empty string -> null (cleared). */
fun validateProfileUpdate(request: UpdateProfileRequest): Checked<Map<String, String?>> {
    val changes = mutableMapOf<String, String?>()
    request.displayName?.let { name ->
        val clean = name.trim()
        if (clean.length > MAX_DISPLAY_NAME) return Checked.Invalid("display_name is too long (max $MAX_DISPLAY_NAME)")
        changes["display_name"] = clean.ifEmpty { null }
    }
    request.bio?.let { bio ->
        val clean = bio.trim()
        if (clean.length > MAX_BIO) return Checked.Invalid("bio is too long (max $MAX_BIO)")
        changes["bio"] = clean.ifEmpty { null }
    }
    if (changes.isEmpty()) return Checked.Invalid("Nothing to update: send display_name and/or bio")
    return Checked.Valid(changes)
}

const val MAX_INTERESTS = 20

fun validateInterests(categoryIds: List<Int>): Checked<List<Int>> {
    val ids = categoryIds.distinct()
    if (ids.any { it <= 0 }) return Checked.Invalid("category_ids must be positive")
    if (ids.size > MAX_INTERESTS) return Checked.Invalid("At most $MAX_INTERESTS interests")
    return Checked.Valid(ids)
}
