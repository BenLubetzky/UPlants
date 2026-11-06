package com.uplants.favorites.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- API responses ---

@Serializable
data class AuthorDto(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class CategoryDto(
    val id: Int,
    val name: String,
    val slug: String,
    val icon: String? = null,
)

/** Enough of a post to draw a card; the full post comes from content-service. */
@Serializable
data class PostSummary(
    val id: String,
    val title: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("rating_avg") val ratingAvg: Double = 0.0,
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("created_at") val createdAt: String,
    val author: AuthorDto? = null,
    val category: CategoryDto? = null,
)

@Serializable
data class FavoriteListDto(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    @SerialName("is_public") val isPublic: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("item_count") val itemCount: Int = 0,
)

@Serializable
data class SavedPost(
    @SerialName("added_at") val addedAt: String,
    val post: PostSummary,
)

@Serializable
data class FavoriteListDetail(
    val id: String,
    val owner: AuthorDto? = null,
    val name: String,
    @SerialName("is_public") val isPublic: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("item_count") val itemCount: Int = 0,
    val posts: List<SavedPost>,
    val limit: Int,
    val offset: Int,
)

/** One of my lists, and whether a given post is in it (for a "save to…" sheet with checkboxes). */
@Serializable
data class ListMembership(
    val id: String,
    val name: String,
    @SerialName("is_public") val isPublic: Boolean,
    @SerialName("contains_post") val containsPost: Boolean,
)

@Serializable
data class ErrorResponse(val error: String)

// --- API requests ---

@Serializable
data class NewListRequest(
    val name: String,
    @SerialName("is_public") val isPublic: Boolean = false,
)

/** Fields left out don't change. */
@Serializable
data class UpdateListRequest(
    val name: String? = null,
    @SerialName("is_public") val isPublic: Boolean? = null,
)

// --- Supabase rows ---

@Serializable
data class CountRow(val count: Int)

/** `favorite_lists` row with `items:favorite_list_items(count)` embedded. */
@Serializable
data class ListRow(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    @SerialName("is_public") val isPublic: Boolean,
    @SerialName("created_at") val createdAt: String,
    val items: List<CountRow> = emptyList(),
    val owner: AuthorDto? = null, // only when embedded
) {
    val itemCount get() = items.firstOrNull()?.count ?: 0

    fun toDto() = FavoriteListDto(id, ownerId, name, isPublic, createdAt, itemCount)
}

/** `favorite_list_items` row with the post embedded (null if it was deleted meanwhile). */
@Serializable
data class ItemRow(
    @SerialName("added_at") val addedAt: String,
    val post: PostSummary? = null,
)

@Serializable
data class ListInsert(
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    @SerialName("is_public") val isPublic: Boolean,
)

@Serializable
data class ItemInsert(
    @SerialName("list_id") val listId: String,
    @SerialName("post_id") val postId: String,
)

@Serializable
data class ListIdRow(@SerialName("list_id") val listId: String)

@Serializable
data class ListNameRow(
    val id: String,
    val name: String,
    @SerialName("is_public") val isPublic: Boolean,
)

@Serializable
data class OwnerRow(@SerialName("owner_id") val ownerId: String)

@Serializable
data class PostgrestError(val code: String? = null, val message: String? = null)

@Serializable
data class SupabaseUser(val id: String)
