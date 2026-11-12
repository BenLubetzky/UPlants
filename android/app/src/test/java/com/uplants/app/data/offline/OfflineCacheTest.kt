package com.uplants.app.data.offline

import com.uplants.app.data.model.ContentTypes
import com.uplants.app.data.model.Post
import com.uplants.app.data.model.PostPage
import com.uplants.app.data.network.ApiErrorKind
import com.uplants.app.data.network.ApiException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfflineCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val page = PostPage(
        posts = listOf(Post(id = "p1", contentType = ContentTypes.TEXT, title = "Fern tips", createdAt = "2026-11-12T10:00:00+00:00")),
        limit = 20,
        offset = 0,
    )

    private fun cache() = OfflineCache(folder.newFolder("offline"))

    @Test
    fun `a good answer is saved and given back when offline, marked as saved content`() = runBlocking {
        val cache = cache()
        val mark = { p: PostPage, at: Long -> p.copy(savedAt = at) }
        val online = cache.withFallback("feed", PostPage.serializer(), mark) { Result.success(page) }
        assertNull(online.getOrThrow().savedAt)

        val offline = cache.withFallback("feed", PostPage.serializer(), mark) {
            Result.failure(ApiException(ApiErrorKind.OFFLINE))
        }
        assertEquals("Fern tips", offline.getOrThrow().posts.single().title)
        assertNotNull(offline.getOrThrow().savedAt)
    }

    @Test
    fun `a service that is down also falls back to saved content`() = runBlocking {
        val cache = cache()
        cache.withFallback("k", PostPage.serializer()) { Result.success(page) }
        val result = cache.withFallback("k", PostPage.serializer()) { Result.failure(ApiException(ApiErrorKind.UNAVAILABLE)) }
        assertTrue(result.isSuccess)
    }

    @Test
    fun `other errors are not hidden by saved content`() = runBlocking {
        val cache = cache()
        cache.withFallback("k", PostPage.serializer()) { Result.success(page) }
        val result = cache.withFallback("k", PostPage.serializer()) { Result.failure(ApiException(ApiErrorKind.NOT_FOUND)) }
        assertEquals(ApiErrorKind.NOT_FOUND, (result.exceptionOrNull() as ApiException).kind)
    }

    @Test
    fun `nothing saved means the error goes through, and clear forgets everything`() = runBlocking {
        val cache = cache()
        val nothing = cache.withFallback("missing", PostPage.serializer()) { Result.failure(ApiException(ApiErrorKind.OFFLINE)) }
        assertTrue(nothing.isFailure)
        cache.put("k", PostPage.serializer(), page)
        cache.clear()
        assertNull(cache.get("k", PostPage.serializer()))
    }
}
