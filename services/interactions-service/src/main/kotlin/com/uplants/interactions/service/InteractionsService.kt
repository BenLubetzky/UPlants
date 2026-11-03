package com.uplants.interactions.service

import com.uplants.interactions.models.CommentDto
import com.uplants.interactions.models.CommentInsert
import com.uplants.interactions.models.CommentOwnerRow
import com.uplants.interactions.models.CommentPage
import com.uplants.interactions.models.MyInteractions
import com.uplants.interactions.models.PostStats
import com.uplants.interactions.models.PostgrestError
import com.uplants.interactions.models.RatingRow
import com.uplants.interactions.models.ReactionRow
import com.uplants.interactions.models.StarsRow
import com.uplants.interactions.models.ViewInsert
import com.uplants.interactions.models.ViewedAtRow
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

sealed interface ServiceResult<out T> {
    data class Ok<T>(val value: T, val status: HttpStatusCode = HttpStatusCode.OK) : ServiceResult<T>
    data class Fail(val status: HttpStatusCode, val message: String) : ServiceResult<Nothing>
}

private val POST_NOT_FOUND = ServiceResult.Fail(HttpStatusCode.NotFound, "Post not found")

/** Comments, likes/dislikes, star ratings and views. Owns `comments`, `reactions`, `ratings`, `post_views`. */
class InteractionsService(private val supabase: SupabaseClient) {

    private val log = LoggerFactory.getLogger(InteractionsService::class.java)

    private companion object {
        const val COMMENT_SELECT = "id,post_id,body,created_at," +
            "author:profiles!comments_author_id_fkey(id,username,display_name,avatar_url)"
        const val SINGLE_OBJECT = "application/vnd.pgrst.object+json"
    }

    // --- Comments ---

    suspend fun listComments(postId: String, limit: Int, offset: Int): ServiceResult<CommentPage> = with(supabase) {
        if (!isUuid(postId)) return POST_NOT_FOUND
        val response = http.get("$restUrl/comments") {
            asService()
            parameter("select", COMMENT_SELECT)
            parameter("post_id", "eq.$postId")
            parameter("order", "created_at.asc")
            parameter("limit", limit)
            parameter("offset", offset)
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        ServiceResult.Ok(CommentPage(response.body(), limit, offset))
    }

    suspend fun addComment(userId: String, postId: String, body: String): ServiceResult<CommentDto> = with(supabase) {
        if (!isUuid(postId)) return POST_NOT_FOUND
        val response = http.post("$restUrl/comments") {
            asService()
            header("Prefer", "return=representation")
            header("Accept", SINGLE_OBJECT)
            parameter("select", COMMENT_SELECT)
            contentType(ContentType.Application.Json)
            setBody(CommentInsert(postId, userId, body))
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        ServiceResult.Ok(response.body(), HttpStatusCode.Created)
    }

    /** Only the comment's author can delete it. */
    suspend fun deleteComment(userId: String, commentId: String): ServiceResult<Unit> = with(supabase) {
        val notFound = ServiceResult.Fail(HttpStatusCode.NotFound, "Comment not found")
        if (!isUuid(commentId)) return notFound
        val lookup = http.get("$restUrl/comments") {
            asService()
            parameter("select", "author_id")
            parameter("id", "eq.$commentId")
        }
        if (!lookup.status.isSuccess()) return upstreamError(lookup)
        val owner = lookup.body<List<CommentOwnerRow>>().firstOrNull() ?: return notFound
        if (owner.authorId != userId) return ServiceResult.Fail(HttpStatusCode.Forbidden, "You can only delete your own comments")

        val response = http.delete("$restUrl/comments") {
            asService()
            parameter("id", "eq.$commentId")
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        ServiceResult.Ok(Unit, HttpStatusCode.NoContent)
    }

    // --- Likes / dislikes and star ratings (one per user per post; sending again replaces it) ---

    suspend fun setReaction(userId: String, postId: String, value: Int) =
        upsert("reactions", postId, ReactionRow(postId, userId, value))

    suspend fun removeReaction(userId: String, postId: String) = remove("reactions", userId, postId)

    suspend fun setRating(userId: String, postId: String, stars: Int) =
        upsert("ratings", postId, RatingRow(postId, userId, stars))

    suspend fun removeRating(userId: String, postId: String) = remove("ratings", userId, postId)

    private suspend inline fun <reified T : Any> upsert(table: String, postId: String, row: T): ServiceResult<Unit> = with(supabase) {
        if (!isUuid(postId)) return POST_NOT_FOUND
        val response = http.post("$restUrl/$table") {
            asService()
            parameter("on_conflict", "post_id,user_id")
            header("Prefer", "resolution=merge-duplicates,return=minimal")
            contentType(ContentType.Application.Json)
            setBody(row)
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        ServiceResult.Ok(Unit, HttpStatusCode.NoContent)
    }

    private suspend fun remove(table: String, userId: String, postId: String): ServiceResult<Unit> = with(supabase) {
        if (!isUuid(postId)) return POST_NOT_FOUND
        val response = http.delete("$restUrl/$table") {
            asService()
            parameter("post_id", "eq.$postId")
            parameter("user_id", "eq.$userId")
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        ServiceResult.Ok(Unit, HttpStatusCode.NoContent)
    }

    /** The user's own like/dislike and stars on a post. */
    suspend fun myInteractions(userId: String, postId: String): ServiceResult<MyInteractions> = with(supabase) {
        if (!isUuid(postId)) return POST_NOT_FOUND
        coroutineScope {
            val reaction = async { firstRow<ReactionRow>("reactions", userId, postId, "post_id,user_id,value") }
            val rating = async { firstRow<RatingRow>("ratings", userId, postId, "post_id,user_id,stars") }
            ServiceResult.Ok(MyInteractions(reaction.await()?.value, rating.await()?.stars))
        }
    }

    private suspend inline fun <reified T> firstRow(table: String, userId: String, postId: String, select: String): T? = with(supabase) {
        val response = http.get("$restUrl/$table") {
            asService()
            parameter("select", select)
            parameter("post_id", "eq.$postId")
            parameter("user_id", "eq.$userId")
        }
        if (!response.status.isSuccess()) error("Supabase ${response.status}: ${response.bodyAsText()}")
        response.body<List<T>>().firstOrNull()
    }

    // --- Views ---

    /** Records a view. A logged-in user opening the same post again within 30 minutes isn't counted twice. */
    suspend fun recordView(viewerId: String?, postId: String): ServiceResult<Unit> = with(supabase) {
        if (!isUuid(postId)) return POST_NOT_FOUND
        if (viewerId != null) {
            val since = Instant.now().minus(VIEW_DEDUP_MINUTES, ChronoUnit.MINUTES).toString()
            val recent = count("post_views", "post_id" to "eq.$postId", "viewer_id" to "eq.$viewerId", "viewed_at" to "gte.$since")
            if (recent != null && recent > 0) return ServiceResult.Ok(Unit, HttpStatusCode.NoContent)
        }
        val response = http.post("$restUrl/post_views") {
            asService()
            header("Prefer", "return=minimal")
            contentType(ContentType.Application.Json)
            setBody(ViewInsert(postId, viewerId))
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        ServiceResult.Ok(Unit, HttpStatusCode.NoContent)
    }

    // --- Dashboard ---

    /** Views (total and per day), comments, likes, dislikes and the star histogram of one post. */
    suspend fun postStats(postId: String): ServiceResult<PostStats> = with(supabase) {
        if (!isUuid(postId)) return POST_NOT_FOUND
        val post = "post_id" to "eq.$postId"
        val since = LocalDate.now(ZoneOffset.UTC).minusDays(STATS_DAYS - 1L).atStartOfDay().toInstant(ZoneOffset.UTC).toString()

        coroutineScope {
            val views = async { count("post_views", post) }
            val comments = async { count("comments", post) }
            val likes = async { count("reactions", post, "value" to "eq.1") }
            val dislikes = async { count("reactions", post, "value" to "eq.-1") }
            val stars = async {
                http.get("$restUrl/ratings") {
                    asService()
                    parameter("select", "stars")
                    parameter("post_id", "eq.$postId")
                }.body<List<StarsRow>>().map { it.stars }
            }
            val recentViews = async {
                http.get("$restUrl/post_views") {
                    asService()
                    parameter("select", "viewed_at")
                    parameter("post_id", "eq.$postId")
                    parameter("viewed_at", "gte.$since")
                    parameter("limit", 10_000)
                }.body<List<ViewedAtRow>>().map { it.viewedAt }
            }

            val allStars = stars.await()
            ServiceResult.Ok(
                PostStats(
                    postId = postId,
                    views = views.await() ?: 0,
                    viewsByDay = viewsByDay(recentViews.await(), LocalDate.now(ZoneOffset.UTC)),
                    comments = comments.await() ?: 0,
                    likes = likes.await() ?: 0,
                    dislikes = dislikes.await() ?: 0,
                    ratingCount = allStars.size,
                    ratingAvg = if (allStars.isEmpty()) 0.0 else (allStars.average() * 100).roundToInt() / 100.0,
                    ratingHistogram = ratingHistogram(allStars),
                )
            )
        }
    }

    /** Turns a Supabase error into something the app can act on. */
    private suspend fun upstreamError(response: HttpResponse): ServiceResult.Fail {
        val text = response.bodyAsText()
        val error = runCatching { supabase.json.decodeFromString<PostgrestError>(text) }.getOrNull()
        return when (error?.code) {
            "23503" -> POST_NOT_FOUND // foreign key: the post (or user) doesn't exist
            "23514", "22P02" -> ServiceResult.Fail(HttpStatusCode.BadRequest, "Invalid data")
            else -> {
                log.error("Supabase error {}: {}", response.status, text)
                ServiceResult.Fail(HttpStatusCode.BadGateway, "Database error")
            }
        }
    }
}
