package com.uplants.content.service

import com.uplants.content.models.CreatePostRequest
import com.uplants.content.models.MediaUpload

/** Input checks with no I/O, so they're easy to unit test. */

sealed interface Checked<out T> {
    data class Valid<T>(val value: T) : Checked<T>
    data class Invalid(val message: String) : Checked<Nothing>
}

val CONTENT_TYPES = setOf("photo", "video", "audio", "text")
val SOURCES = setOf("upload", "live")
const val MAX_MEDIA_BYTES = 50L * 1024 * 1024 // same as the post-media bucket limit

/** Feed orderings. `column` is the PostgREST `order` value. */
enum class Sort(val key: String, val column: String) {
    RECENT("recent", "created_at.desc"),
    RATING("rating", "rating_avg.desc,rating_count.desc,created_at.desc"),
    VIEWS("views", "view_count.desc,created_at.desc"),
    INTERACTIONS("interactions", "interaction_count.desc,created_at.desc");

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key }
    }
}

data class PostQuery(
    val search: String? = null,
    val contentType: String? = null,
    val categoryId: Int? = null,
    val authorId: String? = null,
    val tag: String? = null,
    val sort: Sort = Sort.RECENT,
    val limit: Int = 20,
    val offset: Int = 0,
)

private val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
private val TAG = Regex("^[\\p{L}\\p{N}_-]{1,30}$")

fun isUuid(value: String) = UUID.matches(value)

/** Reads `GET /posts` query parameters. */
fun parsePostQuery(param: (String) -> String?): Checked<PostQuery> {
    val search = param("q")?.trim()?.takeIf { it.isNotEmpty() }
    if (search != null && search.length > 100) return Checked.Invalid("q is too long (max 100)")

    val type = param("type")?.lowercase()
    if (type != null && type !in CONTENT_TYPES) return Checked.Invalid("type must be one of $CONTENT_TYPES")

    val category = param("category")?.let { it.toIntOrNull() ?: return Checked.Invalid("category must be a number") }

    val author = param("author")
    if (author != null && !isUuid(author)) return Checked.Invalid("author must be a user id")

    val tag = param("tag")?.lowercase()
    if (tag != null && !TAG.matches(tag)) return Checked.Invalid("invalid tag")

    val sort = param("sort")?.let { Sort.of(it) ?: return Checked.Invalid("sort must be one of ${Sort.entries.map { s -> s.key }}") }
        ?: Sort.RECENT

    val limit = param("limit")?.let { it.toIntOrNull()?.takeIf { n -> n in 1..50 } ?: return Checked.Invalid("limit must be 1-50") } ?: 20
    val offset = param("offset")?.let { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return Checked.Invalid("offset must be 0 or more") } ?: 0

    return Checked.Valid(PostQuery(search, type, category, author, tag, sort, limit, offset))
}

/** Checks and cleans up a new post (trims text, lowercases tags). */
fun validateNewPost(req: CreatePostRequest, media: MediaUpload?): Checked<CreatePostRequest> {
    val title = req.title.trim()
    if (title.isEmpty() || title.length > 120) return Checked.Invalid("title must be 1-120 characters")

    val description = req.description?.trim()?.takeIf { it.isNotEmpty() }
    if (description != null && description.length > 2000) return Checked.Invalid("description is too long (max 2000)")

    if (req.contentType !in CONTENT_TYPES) return Checked.Invalid("content_type must be one of $CONTENT_TYPES")
    if (req.source !in SOURCES) return Checked.Invalid("source must be one of $SOURCES")
    if (req.categoryId <= 0) return Checked.Invalid("category_id is required")

    if ((req.latitude == null) != (req.longitude == null)) return Checked.Invalid("send both latitude and longitude, or neither")
    if (req.latitude != null && req.latitude !in -90.0..90.0) return Checked.Invalid("latitude must be between -90 and 90")
    if (req.longitude != null && req.longitude !in -180.0..180.0) return Checked.Invalid("longitude must be between -180 and 180")
    // Live recordings must come with device data (assignment: GPS, sensors, camera).
    if (req.source == "live" && req.latitude == null) return Checked.Invalid("live posts need latitude and longitude")

    if (req.durationSeconds != null && req.durationSeconds < 0) return Checked.Invalid("duration_seconds can't be negative")

    val tags = req.tags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
    if (tags.size > 10) return Checked.Invalid("at most 10 tags")
    if (tags.any { !TAG.matches(it) }) return Checked.Invalid("tags may only use letters, numbers, _ and - (max 30)")

    mediaError(req.contentType, media)?.let { return Checked.Invalid(it) }

    return Checked.Valid(
        req.copy(
            title = title,
            description = description,
            plantSpecies = req.plantSpecies?.trim()?.takeIf { it.isNotEmpty() },
            locationName = req.locationName?.trim()?.takeIf { it.isNotEmpty() },
            tags = tags,
        )
    )
}

private fun mediaError(contentType: String, media: MediaUpload?): String? {
    if (contentType == "text") return if (media != null) "text posts don't take a file" else null
    if (media == null) return "$contentType posts need a file"
    if (media.bytes.isEmpty()) return "the file is empty"
    if (media.bytes.size > MAX_MEDIA_BYTES) return "the file is too big (max 50 MB)"

    val expected = when (contentType) {
        "photo" -> "image/"
        "video" -> "video/"
        else -> "audio/"
    }
    val mime = media.mimeType?.lowercase()
    if (mime == null || !mime.startsWith(expected)) return "$contentType posts need a $expected* file (got ${mime ?: "no type"})"
    return null
}

/** File extension for the stored object, e.g. "jpg". */
fun extensionFor(media: MediaUpload): String {
    media.fileName?.substringAfterLast('.', "")?.lowercase()
        ?.takeIf { it.isNotEmpty() && it.length <= 5 && it.all(Char::isLetterOrDigit) }
        ?.let { return it }
    return when (val subtype = media.mimeType?.substringAfter('/')?.lowercase()) {
        "jpeg" -> "jpg"
        "mpeg" -> "mp3"
        "quicktime" -> "mov"
        null -> "bin"
        else -> subtype.filter(Char::isLetterOrDigit).take(5).ifEmpty { "bin" }
    }
}
