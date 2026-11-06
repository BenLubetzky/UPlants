package com.uplants.favorites.service

import com.uplants.favorites.models.FavoriteListDetail
import com.uplants.favorites.models.FavoriteListDto
import com.uplants.favorites.models.ItemInsert
import com.uplants.favorites.models.ItemRow
import com.uplants.favorites.models.ListIdRow
import com.uplants.favorites.models.ListInsert
import com.uplants.favorites.models.ListMembership
import com.uplants.favorites.models.ListNameRow
import com.uplants.favorites.models.ListRow
import com.uplants.favorites.models.OwnerRow
import com.uplants.favorites.models.PostgrestError
import com.uplants.favorites.models.SavedPost
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

sealed interface ServiceResult<out T> {
    data class Ok<T>(val value: T, val status: HttpStatusCode = HttpStatusCode.OK) : ServiceResult<T>
    data class Fail(val status: HttpStatusCode, val message: String) : ServiceResult<Nothing>
}

private val LIST_NOT_FOUND = ServiceResult.Fail(HttpStatusCode.NotFound, "List not found")
private val POST_NOT_FOUND = ServiceResult.Fail(HttpStatusCode.NotFound, "Post not found")
private val NAME_TAKEN = ServiceResult.Fail(HttpStatusCode.Conflict, "You already have a list with that name")
private val NOT_YOURS = ServiceResult.Fail(HttpStatusCode.Forbidden, "Only the owner can change this list")

/** Named favourite lists of posts. Owns `favorite_lists` and `favorite_list_items`. */
class FavoritesService(private val supabase: SupabaseClient) {

    private val log = LoggerFactory.getLogger(FavoritesService::class.java)

    private companion object {
        const val LIST_SELECT = "id,owner_id,name,is_public,created_at,items:favorite_list_items(count)"
        const val OWNER_EMBED = "owner:profiles!favorite_lists_owner_id_fkey(id,username,display_name,avatar_url)"
        const val POST_SUMMARY = "post:posts!favorite_list_items_post_id_fkey(" +
            "id,title,content_type,media_url,thumbnail_url,rating_avg,like_count,comment_count,created_at," +
            "author:profiles!posts_author_id_fkey(id,username,display_name,avatar_url)," +
            "category:categories!posts_category_id_fkey(id,name,slug,icon))"
        const val SINGLE_OBJECT = "application/vnd.pgrst.object+json"
    }

    // --- Lists ---

    /** [ownerId]'s lists, newest first. Only public ones unless [includePrivate]. */
    suspend fun listsOf(ownerId: String, includePrivate: Boolean): ServiceResult<List<FavoriteListDto>> = with(supabase) {
        if (!isUuid(ownerId)) return ServiceResult.Fail(HttpStatusCode.NotFound, "User not found")
        val response = http.get("$restUrl/favorite_lists") {
            asService()
            parameter("select", LIST_SELECT)
            parameter("owner_id", "eq.$ownerId")
            if (!includePrivate) parameter("is_public", "is.true")
            parameter("order", "created_at.desc")
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        ServiceResult.Ok(response.body<List<ListRow>>().map { it.toDto() })
    }

    /** `name` must be validated. */
    suspend fun createList(ownerId: String, name: String, isPublic: Boolean): ServiceResult<FavoriteListDto> = with(supabase) {
        if (isNameTaken(name, myListNames(ownerId).map { it.id to it.name })) return NAME_TAKEN
        val response = http.post("$restUrl/favorite_lists") {
            asService()
            header("Prefer", "return=representation")
            header("Accept", SINGLE_OBJECT)
            parameter("select", LIST_SELECT)
            contentType(ContentType.Application.Json)
            setBody(ListInsert(ownerId, name, isPublic))
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        ServiceResult.Ok(response.body<ListRow>().toDto(), HttpStatusCode.Created)
    }

    /** A list and its posts (newest saved first). Private lists are only visible to their owner. */
    suspend fun listDetail(listId: String, viewerId: String?, limit: Int, offset: Int): ServiceResult<FavoriteListDetail> = with(supabase) {
        if (!isUuid(listId)) return LIST_NOT_FOUND
        coroutineScope {
            val row = async { listRow(listId) }
            val items = async {
                val response = http.get("$restUrl/favorite_list_items") {
                    asService()
                    parameter("select", "added_at,$POST_SUMMARY")
                    parameter("list_id", "eq.$listId")
                    parameter("order", "added_at.desc")
                    parameter("limit", limit)
                    parameter("offset", offset)
                }
                if (!response.status.isSuccess()) error("Supabase ${response.status}: ${response.bodyAsText()}")
                response.body<List<ItemRow>>()
            }

            val list = row.await() ?: return@coroutineScope LIST_NOT_FOUND
            // Someone else's private list: answer as if it didn't exist.
            if (!list.isPublic && list.ownerId != viewerId) return@coroutineScope LIST_NOT_FOUND
            ServiceResult.Ok(
                FavoriteListDetail(
                    id = list.id,
                    owner = list.owner,
                    name = list.name,
                    isPublic = list.isPublic,
                    createdAt = list.createdAt,
                    itemCount = list.itemCount,
                    posts = items.await().mapNotNull { item -> item.post?.let { SavedPost(item.addedAt, it) } },
                    limit = limit,
                    offset = offset,
                )
            )
        }
    }

    /** Rename and/or change visibility. `changes` must be validated. */
    suspend fun updateList(userId: String, listId: String, changes: ListChanges): ServiceResult<FavoriteListDto> = with(supabase) {
        checkOwner(userId, listId)?.let { return it }
        if (changes.name != null && isNameTaken(changes.name, myListNames(userId).map { it.id to it.name }, exceptListId = listId)) {
            return NAME_TAKEN
        }
        val response = http.patch("$restUrl/favorite_lists") {
            asService()
            header("Prefer", "return=representation")
            header("Accept", SINGLE_OBJECT)
            parameter("select", LIST_SELECT)
            parameter("id", "eq.$listId")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                changes.name?.let { put("name", it) }
                changes.isPublic?.let { put("is_public", it) }
            })
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        ServiceResult.Ok(response.body<ListRow>().toDto())
    }

    /** Deletes the list; its items go with it (ON DELETE CASCADE). */
    suspend fun deleteList(userId: String, listId: String): ServiceResult<Unit> = with(supabase) {
        checkOwner(userId, listId)?.let { return it }
        val response = http.delete("$restUrl/favorite_lists") {
            asService()
            parameter("id", "eq.$listId")
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        ServiceResult.Ok(Unit, HttpStatusCode.NoContent)
    }

    // --- Posts in a list ---

    /** Saving the same post twice is fine. */
    suspend fun addPost(userId: String, listId: String, postId: String): ServiceResult<Unit> = with(supabase) {
        if (!isUuid(postId)) return POST_NOT_FOUND
        checkOwner(userId, listId)?.let { return it }
        val response = http.post("$restUrl/favorite_list_items") {
            asService()
            parameter("on_conflict", "list_id,post_id")
            header("Prefer", "resolution=ignore-duplicates,return=minimal")
            contentType(ContentType.Application.Json)
            setBody(ItemInsert(listId, postId))
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        ServiceResult.Ok(Unit, HttpStatusCode.NoContent)
    }

    suspend fun removePost(userId: String, listId: String, postId: String): ServiceResult<Unit> = with(supabase) {
        if (!isUuid(postId)) return POST_NOT_FOUND
        checkOwner(userId, listId)?.let { return it }
        val response = http.delete("$restUrl/favorite_list_items") {
            asService()
            parameter("list_id", "eq.$listId")
            parameter("post_id", "eq.$postId")
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        ServiceResult.Ok(Unit, HttpStatusCode.NoContent)
    }

    /** All my lists, each saying whether [postId] is in it. */
    suspend fun membership(userId: String, postId: String): ServiceResult<List<ListMembership>> = with(supabase) {
        if (!isUuid(postId)) return POST_NOT_FOUND
        val lists = myListNames(userId)
        if (lists.isEmpty()) return ServiceResult.Ok(emptyList())
        val response = http.get("$restUrl/favorite_list_items") {
            asService()
            parameter("select", "list_id")
            parameter("post_id", "eq.$postId")
            parameter("list_id", "in.(${lists.joinToString(",") { it.id }})")
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        val containing = response.body<List<ListIdRow>>().map { it.listId }.toSet()
        ServiceResult.Ok(lists.map { ListMembership(it.id, it.name, it.isPublic, it.id in containing) })
    }

    // --- Helpers ---

    private suspend fun myListNames(userId: String): List<ListNameRow> = with(supabase) {
        val response = http.get("$restUrl/favorite_lists") {
            asService()
            parameter("select", "id,name,is_public")
            parameter("owner_id", "eq.$userId")
            parameter("order", "created_at.desc")
        }
        if (!response.status.isSuccess()) error("Supabase ${response.status}: ${response.bodyAsText()}")
        response.body()
    }

    private suspend fun listRow(listId: String): ListRow? = with(supabase) {
        val response = http.get("$restUrl/favorite_lists") {
            asService()
            header("Accept", SINGLE_OBJECT)
            parameter("select", "$LIST_SELECT,$OWNER_EMBED")
            parameter("id", "eq.$listId")
        }
        // With the single-object header, "no rows" comes back as 406.
        if (response.status == HttpStatusCode.NotAcceptable) return null
        if (!response.status.isSuccess()) error("Supabase ${response.status}: ${response.bodyAsText()}")
        response.body()
    }

    /** Null if [userId] owns the list, otherwise the error to answer with. */
    private suspend fun checkOwner(userId: String, listId: String): ServiceResult.Fail? = with(supabase) {
        if (!isUuid(listId)) return LIST_NOT_FOUND
        val response = http.get("$restUrl/favorite_lists") {
            asService()
            parameter("select", "owner_id")
            parameter("id", "eq.$listId")
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        val owner = response.body<List<OwnerRow>>().firstOrNull() ?: return LIST_NOT_FOUND
        if (owner.ownerId != userId) NOT_YOURS else null
    }

    /** Turns a Supabase error into something the app can act on. */
    private suspend fun upstreamError(response: HttpResponse): ServiceResult.Fail {
        val text = response.bodyAsText()
        val error = runCatching { supabase.json.decodeFromString<PostgrestError>(text) }.getOrNull()
        return when (error?.code) {
            "23503" -> POST_NOT_FOUND // foreign key: the post doesn't exist
            "23514", "22P02", "22001" -> ServiceResult.Fail(HttpStatusCode.BadRequest, "Invalid data")
            else -> {
                log.error("Supabase error {}: {}", response.status, text)
                ServiceResult.Fail(HttpStatusCode.BadGateway, "Database error")
            }
        }
    }
}
