package com.uplants.favorites.service

import com.uplants.favorites.models.UpdateListRequest

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

const val MAX_LIST_NAME = 60 // same as the database check

/** Trims the name and collapses inner whitespace ("My   ferns" -> "My ferns"). */
fun validateListName(name: String): Checked<String> {
    val clean = name.trim().replace(Regex("\\s+"), " ")
    if (clean.isEmpty()) return Checked.Invalid("name can't be empty")
    if (clean.length > MAX_LIST_NAME) return Checked.Invalid("name is too long (max $MAX_LIST_NAME)")
    return Checked.Valid(clean)
}

/** What to change in a list; null = keep. */
data class ListChanges(val name: String?, val isPublic: Boolean?)

fun validateListUpdate(request: UpdateListRequest): Checked<ListChanges> {
    val name = request.name?.let { name ->
        when (val checked = validateListName(name)) {
            is Checked.Invalid -> return checked
            is Checked.Valid -> checked.value
        }
    }
    if (name == null && request.isPublic == null) return Checked.Invalid("Nothing to update: send name and/or is_public")
    return Checked.Valid(ListChanges(name, request.isPublic))
}

/** True if [name] is already used by one of [existing] (ignoring case), other than the list being renamed. */
fun isNameTaken(name: String, existing: List<Pair<String, String>>, exceptListId: String? = null): Boolean =
    existing.any { (id, other) -> id != exceptListId && other.equals(name, ignoreCase = true) }
