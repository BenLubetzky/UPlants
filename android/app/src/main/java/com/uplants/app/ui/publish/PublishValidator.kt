package com.uplants.app.ui.publish

import androidx.annotation.StringRes
import com.uplants.app.R
import com.uplants.app.data.model.ContentTypes

/** What the form checks before sending; the same rules content-service applies (services/content-service/README.md). */
data class PublishInput(
    val contentType: String,
    val title: String,
    val description: String,
    val categoryId: Int?,
    val tagsText: String,
    val mediaMimeType: String?,
    val mediaSizeBytes: Long?,
    val isLive: Boolean,
    val hasLocation: Boolean,
)

/** One message per field; null = fine. */
data class PublishErrors(
    @StringRes val title: Int? = null,
    @StringRes val description: Int? = null,
    @StringRes val category: Int? = null,
    @StringRes val tags: Int? = null,
    @StringRes val media: Int? = null,
    @StringRes val location: Int? = null,
) {
    val hasErrors: Boolean get() = listOf(title, description, category, tags, media, location).any { it != null }
}

object PublishValidator {
    const val MAX_TITLE = 120
    const val MAX_DESCRIPTION = 2000
    const val MAX_TAGS = 10
    const val MAX_FILE_BYTES = 50L * 1024 * 1024

    private val tagRegex = Regex("^[\\p{L}\\p{N}_-]{1,30}$")

    /** "Fern, #indoor  watering" → [fern, indoor, watering]. */
    fun parseTags(text: String): List<String> =
        text.split(',', ' ', '#', '\n').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()

    fun validate(input: PublishInput): PublishErrors {
        val title = input.title.trim()
        val tags = parseTags(input.tagsText)
        return PublishErrors(
            title = when {
                title.isEmpty() -> R.string.publish_error_title_required
                title.length > MAX_TITLE -> R.string.publish_error_title_long
                else -> null
            },
            description = R.string.publish_error_description_long.takeIf { input.description.trim().length > MAX_DESCRIPTION },
            category = R.string.publish_error_category.takeIf { input.categoryId == null },
            tags = when {
                tags.size > MAX_TAGS -> R.string.publish_error_too_many_tags
                tags.any { !tagRegex.matches(it) } -> R.string.publish_error_tag_format
                else -> null
            },
            media = mediaError(input),
            // Live recordings must carry GPS (assignment requirement 18; the service refuses them otherwise).
            location = R.string.publish_error_location_needed.takeIf { input.isLive && !input.hasLocation },
        )
    }

    @StringRes
    private fun mediaError(input: PublishInput): Int? {
        if (input.contentType == ContentTypes.TEXT) return null
        val mime = input.mediaMimeType ?: return R.string.publish_error_media_required
        if ((input.mediaSizeBytes ?: 0) > MAX_FILE_BYTES) return R.string.publish_error_file_too_big
        val expected = when (input.contentType) {
            ContentTypes.PHOTO -> "image/"
            ContentTypes.VIDEO -> "video/"
            else -> "audio/"
        }
        return R.string.publish_error_media_type.takeIf { !mime.startsWith(expected) }
    }
}
