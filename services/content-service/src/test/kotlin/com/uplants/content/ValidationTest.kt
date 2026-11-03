package com.uplants.content

import com.uplants.content.models.CreatePostRequest
import com.uplants.content.models.MediaUpload
import com.uplants.content.service.Checked
import com.uplants.content.service.PostQuery
import com.uplants.content.service.Sort
import com.uplants.content.service.extensionFor
import com.uplants.content.service.parsePostQuery
import com.uplants.content.service.validateNewPost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ValidationTest {

    private fun query(vararg params: Pair<String, String>) = parsePostQuery { params.toMap()[it] }

    private val photo = MediaUpload(ByteArray(10), "image/jpeg", "leaf.JPG")
    private fun post(
        type: String = "photo",
        source: String = "upload",
        lat: Double? = null,
        lon: Double? = null,
        tags: List<String> = emptyList(),
    ) = CreatePostRequest(
        categoryId = 1, contentType = type, source = source, title = "  My fern  ",
        latitude = lat, longitude = lon, tags = tags,
    )

    @Test
    fun `feed query has sensible defaults`() {
        val q = assertIs<Checked.Valid<*>>(query()).value as PostQuery
        assertEquals(Sort.RECENT, q.sort)
        assertEquals(20, q.limit)
        assertEquals(0, q.offset)
    }

    @Test
    fun `feed query reads filters and sort`() {
        val q = assertIs<Checked.Valid<*>>(
            query("sort" to "rating", "type" to "VIDEO", "category" to "3", "limit" to "50", "q" to " ferns ")
        ).value as PostQuery
        assertEquals(Sort.RATING, q.sort)
        assertEquals("video", q.contentType)
        assertEquals(3, q.categoryId)
        assertEquals("ferns", q.search)
    }

    @Test
    fun `feed query rejects bad values`() {
        assertIs<Checked.Invalid>(query("sort" to "random"))
        assertIs<Checked.Invalid>(query("type" to "gif"))
        assertIs<Checked.Invalid>(query("limit" to "500"))
        assertIs<Checked.Invalid>(query("author" to "not-a-uuid"))
        assertIs<Checked.Invalid>(query("tag" to "a,b"))
    }

    @Test
    fun `valid photo post is cleaned up`() {
        val checked = assertIs<Checked.Valid<CreatePostRequest>>(validateNewPost(post(tags = listOf(" Fern", "fern", "Indoor")), photo))
        assertEquals("My fern", checked.value.title)
        assertEquals(listOf("fern", "indoor"), checked.value.tags)
    }

    @Test
    fun `media must match the content type`() {
        assertIs<Checked.Invalid>(validateNewPost(post(type = "photo"), null))
        assertIs<Checked.Invalid>(validateNewPost(post(type = "video"), photo))
        assertIs<Checked.Invalid>(validateNewPost(post(type = "text"), photo))
        assertIs<Checked.Valid<*>>(validateNewPost(post(type = "text"), null))
    }

    @Test
    fun `live posts need a location`() {
        assertIs<Checked.Invalid>(validateNewPost(post(source = "live"), photo))
        assertIs<Checked.Valid<*>>(validateNewPost(post(source = "live", lat = 41.15, lon = -8.61), photo))
    }

    @Test
    fun `coordinates must be complete and in range`() {
        assertIs<Checked.Invalid>(validateNewPost(post(lat = 41.0), photo))
        assertIs<Checked.Invalid>(validateNewPost(post(lat = 91.0, lon = 0.0), photo))
    }

    @Test
    fun `file extension comes from the name, then the mime type`() {
        assertEquals("jpg", extensionFor(photo))
        assertEquals("mp3", extensionFor(MediaUpload(ByteArray(1), "audio/mpeg", null)))
        assertEquals("bin", extensionFor(MediaUpload(ByteArray(1), null, null)))
    }
}
