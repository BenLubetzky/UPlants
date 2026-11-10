package com.uplants.app.ui.publish

import com.uplants.app.R
import com.uplants.app.data.model.ContentTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PublishValidatorTest {

    private val validPhoto = PublishInput(
        contentType = ContentTypes.PHOTO,
        title = "My fern",
        description = "",
        categoryId = 1,
        tagsText = "fern, indoor",
        mediaMimeType = "image/jpeg",
        mediaSizeBytes = 2_000_000,
        isLive = true,
        hasLocation = true,
    )

    @Test
    fun `complete live photo is valid`() {
        assertFalse(PublishValidator.validate(validPhoto).hasErrors)
    }

    @Test
    fun `text post needs no file`() {
        val errors = PublishValidator.validate(validPhoto.copy(contentType = ContentTypes.TEXT, mediaMimeType = null, isLive = false))
        assertFalse(errors.hasErrors)
    }

    @Test
    fun `missing title, category and file are reported`() {
        val errors = PublishValidator.validate(validPhoto.copy(title = "  ", categoryId = null, mediaMimeType = null))
        assertEquals(R.string.publish_error_title_required, errors.title)
        assertEquals(R.string.publish_error_category, errors.category)
        assertEquals(R.string.publish_error_media_required, errors.media)
    }

    @Test
    fun `live recording without location is refused`() {
        assertEquals(R.string.publish_error_location_needed, PublishValidator.validate(validPhoto.copy(hasLocation = false)).location)
        // An upload without location is fine.
        assertNull(PublishValidator.validate(validPhoto.copy(isLive = false, hasLocation = false)).location)
    }

    @Test
    fun `file must match the post type and size limit`() {
        assertEquals(R.string.publish_error_media_type, PublishValidator.validate(validPhoto.copy(mediaMimeType = "audio/mp4")).media)
        assertEquals(R.string.publish_error_file_too_big, PublishValidator.validate(validPhoto.copy(mediaSizeBytes = 60L * 1024 * 1024)).media)
    }

    @Test
    fun `tags are split, lowercased and checked`() {
        assertEquals(listOf("fern", "indoor", "watering"), PublishValidator.parseTags("Fern, #indoor  watering fern"))
        assertEquals(R.string.publish_error_tag_format, PublishValidator.validate(validPhoto.copy(tagsText = "ok, not!ok")).tags)
        assertEquals(R.string.publish_error_too_many_tags, PublishValidator.validate(validPhoto.copy(tagsText = (1..11).joinToString(" ") { "t$it" })).tags)
    }
}
