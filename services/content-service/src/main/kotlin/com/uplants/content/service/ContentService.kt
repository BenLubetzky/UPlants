package com.uplants.content.service

import com.uplants.content.models.CategoryDto
import com.uplants.content.models.CreatePostRequest
import com.uplants.content.models.MediaUpload
import com.uplants.content.models.PostDto
import com.uplants.content.models.PostInsert
import com.uplants.content.models.PostOwnerRow
import com.uplants.content.models.PostPage
import com.uplants.content.models.PostgrestError
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import org.slf4j.LoggerFactory
import java.util.UUID

sealed interface ServiceResult<out T> {
    data class Ok<T>(val value: T, val status: HttpStatusCode = HttpStatusCode.OK) : ServiceResult<T>
    data class Fail(val status: HttpStatusCode, val message: String) : ServiceResult<Nothing>
}

/** Posts and categories. Owns the `posts` and `categories` tables and the `post-media` bucket. */
class ContentService(private val supabase: SupabaseClient) {

    private val log = LoggerFactory.getLogger(ContentService::class.java)

    private companion object {
        // Post columns plus author and category, embedded through their foreign keys.
        const val POST_SELECT = "id,content_type,source,title,description,plant_species,tags," +
            "media_url,thumbnail_url,duration_seconds,latitude,longitude,location_name,sensor_data," +
            "view_count,like_count,dislike_count,comment_count,share_count,rating_avg,rating_count," +
            "interaction_count,created_at,updated_at," +
            "author:profiles!posts_author_id_fkey(id,username,display_name,avatar_url)," +
            "category:categories!posts_category_id_fkey(id,name,slug,icon)"
        const val SINGLE_OBJECT = "application/vnd.pgrst.object+json"
    }

    suspend fun listCategories(): ServiceResult<List<CategoryDto>> = with(supabase) {
        val response = http.get("$restUrl/categories") {
            asService()
            parameter("select", "id,name,slug,icon")
            parameter("order", "name.asc")
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        ServiceResult.Ok(response.body())
    }

    suspend fun listPosts(query: PostQuery): ServiceResult<PostPage> = with(supabase) {
        val response = http.get("$restUrl/posts") {
            asService()
            parameter("select", POST_SELECT)
            query.search?.let { parameter("search_vector", "wfts(english).$it") }
            query.contentType?.let { parameter("content_type", "eq.$it") }
            query.categoryId?.let { parameter("category_id", "eq.$it") }
            query.authorId?.let { parameter("author_id", "eq.$it") }
            query.tag?.let { parameter("tags", "cs.{$it}") }
            parameter("order", query.sort.column)
            parameter("limit", query.limit)
            parameter("offset", query.offset)
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        ServiceResult.Ok(PostPage(response.body(), query.limit, query.offset))
    }

    suspend fun getPost(id: String): ServiceResult<PostDto> = with(supabase) {
        if (!isUuid(id)) return ServiceResult.Fail(HttpStatusCode.NotFound, "Post not found")
        val response = http.get("$restUrl/posts") {
            asService()
            header("Accept", SINGLE_OBJECT)
            parameter("select", POST_SELECT)
            parameter("id", "eq.$id")
        }
        // With the single-object header, "no rows" comes back as 406.
        if (response.status == HttpStatusCode.NotAcceptable) return ServiceResult.Fail(HttpStatusCode.NotFound, "Post not found")
        if (!response.status.isSuccess()) return upstreamError(response)
        ServiceResult.Ok(response.body())
    }

    /** Uploads the file (if any), then inserts the post. `post` must already be validated. */
    suspend fun createPost(authorId: String, post: CreatePostRequest, media: MediaUpload?): ServiceResult<PostDto> = with(supabase) {
        var mediaPath: String? = null
        if (media != null) {
            mediaPath = "$authorId/${UUID.randomUUID()}.${extensionFor(media)}"
            val upload = http.post("$storageUrl/object/$MEDIA_BUCKET/$mediaPath") {
                asService()
                header("x-upsert", "false")
                contentType(ContentType.parse(media.mimeType ?: "application/octet-stream"))
                setBody(media.bytes)
            }
            if (!upload.status.isSuccess()) {
                log.warn("Upload failed: {} {}", upload.status, upload.bodyAsText())
                return ServiceResult.Fail(HttpStatusCode.BadGateway, "Couldn't store the file")
            }
        }

        val response = http.post("$restUrl/posts") {
            asService()
            header("Prefer", "return=representation")
            header("Accept", SINGLE_OBJECT)
            parameter("select", POST_SELECT)
            contentType(ContentType.Application.Json)
            setBody(
                PostInsert(
                    authorId = authorId,
                    categoryId = post.categoryId,
                    contentType = post.contentType,
                    source = post.source,
                    title = post.title,
                    description = post.description,
                    plantSpecies = post.plantSpecies,
                    tags = post.tags,
                    mediaUrl = mediaPath?.let(::publicMediaUrl),
                    durationSeconds = post.durationSeconds,
                    latitude = post.latitude,
                    longitude = post.longitude,
                    locationName = post.locationName,
                    sensorData = post.sensorData,
                )
            )
        }
        if (!response.status.isSuccess()) {
            mediaPath?.let { deleteMedia(it) } // don't leave an orphan file behind
            return upstreamError(response)
        }
        ServiceResult.Ok(response.body(), HttpStatusCode.Created)
    }

    /** Deletes a post and its file. Only the author can do it. */
    suspend fun deletePost(userId: String, postId: String): ServiceResult<Unit> = with(supabase) {
        if (!isUuid(postId)) return ServiceResult.Fail(HttpStatusCode.NotFound, "Post not found")

        val lookup = http.get("$restUrl/posts") {
            asService()
            parameter("select", "author_id,media_url")
            parameter("id", "eq.$postId")
        }
        if (!lookup.status.isSuccess()) return upstreamError(lookup)
        val owner = lookup.body<List<PostOwnerRow>>().firstOrNull()
            ?: return ServiceResult.Fail(HttpStatusCode.NotFound, "Post not found")
        if (owner.authorId != userId) return ServiceResult.Fail(HttpStatusCode.Forbidden, "You can only delete your own posts")

        val response = http.delete("$restUrl/posts") {
            asService()
            parameter("id", "eq.$postId")
        }
        if (!response.status.isSuccess()) return upstreamError(response)

        owner.mediaUrl?.let(::mediaPathOf)?.let { deleteMedia(it) }
        ServiceResult.Ok(Unit, HttpStatusCode.NoContent)
    }

    /** Best effort: a leftover file is not worth failing the request for. */
    private suspend fun deleteMedia(path: String) = with(supabase) {
        val response = http.delete("$storageUrl/object/$MEDIA_BUCKET/$path") { asService() }
        if (!response.status.isSuccess()) log.warn("Couldn't delete media {}: {}", path, response.status)
    }

    /** Turns a Supabase error into something the app can act on. */
    private suspend fun upstreamError(response: HttpResponse): ServiceResult.Fail {
        val text = response.bodyAsText()
        val error = runCatching { supabase.json.decodeFromString<PostgrestError>(text) }.getOrNull()
        return when (error?.code) {
            "23503" -> ServiceResult.Fail(HttpStatusCode.BadRequest, "Unknown category or author")
            "23514", "22P02", "22023" -> ServiceResult.Fail(HttpStatusCode.BadRequest, "Invalid post data")
            else -> {
                log.error("Supabase error {}: {}", response.status, text)
                ServiceResult.Fail(HttpStatusCode.BadGateway, "Database error")
            }
        }
    }
}
