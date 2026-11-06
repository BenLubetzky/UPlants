package com.uplants.content.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// --- API responses ---

@Serializable
data class CategoryDto(
    val id: Int,
    val name: String,
    val slug: String,
    val icon: String? = null,
)

@Serializable
data class AuthorDto(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class PostDto(
    val id: String,
    val author: AuthorDto? = null,
    val category: CategoryDto? = null,
    @SerialName("content_type") val contentType: String,
    val source: String,
    val title: String,
    val description: String? = null,
    @SerialName("plant_species") val plantSpecies: String? = null,
    val tags: List<String> = emptyList(),
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("location_name") val locationName: String? = null,
    @SerialName("sensor_data") val sensorData: JsonObject? = null,
    @SerialName("view_count") val viewCount: Int = 0,
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("dislike_count") val dislikeCount: Int = 0,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("share_count") val shareCount: Int = 0,
    @SerialName("rating_avg") val ratingAvg: Double = 0.0,
    @SerialName("rating_count") val ratingCount: Int = 0,
    @SerialName("interaction_count") val interactionCount: Int = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class PostPage(
    val posts: List<PostDto>,
    val limit: Int,
    val offset: Int,
)

/** `GET /api/content/feed`: like [PostPage], plus whether it was personalised (false = popular posts for a new user). */
@Serializable
data class FeedPage(
    val posts: List<PostDto>,
    val limit: Int,
    val offset: Int,
    val personalized: Boolean,
)

@Serializable
data class ErrorResponse(val error: String)

// --- API requests ---

/** The `metadata` part of `POST /api/content/posts` (multipart). */
@Serializable
data class CreatePostRequest(
    @SerialName("category_id") val categoryId: Int,
    @SerialName("content_type") val contentType: String,
    val source: String = "upload",
    val title: String,
    val description: String? = null,
    @SerialName("plant_species") val plantSpecies: String? = null,
    val tags: List<String> = emptyList(),
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("location_name") val locationName: String? = null,
    @SerialName("sensor_data") val sensorData: JsonObject? = null,
)

/** The `file` part of `POST /api/content/posts`. */
class MediaUpload(
    val bytes: ByteArray,
    val mimeType: String?,
    val fileName: String?,
)

// --- Supabase (internal) ---

@Serializable
data class PostInsert(
    @SerialName("author_id") val authorId: String,
    @SerialName("category_id") val categoryId: Int,
    @SerialName("content_type") val contentType: String,
    val source: String,
    val title: String,
    val description: String? = null,
    @SerialName("plant_species") val plantSpecies: String? = null,
    val tags: List<String> = emptyList(),
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("location_name") val locationName: String? = null,
    @SerialName("sensor_data") val sensorData: JsonObject? = null,
)

@Serializable
data class PostOwnerRow(
    @SerialName("author_id") val authorId: String,
    @SerialName("media_url") val mediaUrl: String? = null,
)

@Serializable
data class PostgrestError(
    val code: String? = null,
    val message: String? = null,
)

@Serializable
data class SupabaseUser(val id: String)

// --- Read-only rows from users-service tables (feed signals) ---

@Serializable
data class FollowingRow(@SerialName("following_id") val followingId: String)

@Serializable
data class InterestCategoryRow(@SerialName("category_id") val categoryId: Int)
