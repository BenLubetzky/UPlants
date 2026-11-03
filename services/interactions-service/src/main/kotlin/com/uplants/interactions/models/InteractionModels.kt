package com.uplants.interactions.models

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
data class CommentDto(
    val id: String,
    @SerialName("post_id") val postId: String,
    val author: AuthorDto? = null,
    val body: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class CommentPage(
    val comments: List<CommentDto>,
    val limit: Int,
    val offset: Int,
)

/** What the logged-in user has done on a post, to highlight the buttons. */
@Serializable
data class MyInteractions(
    val reaction: Int? = null, // 1 = like, -1 = dislike, null = none
    val stars: Int? = null,    // 0-5, null = not rated
)

@Serializable
data class DayCount(val date: String, val count: Int)

/** Dashboard numbers for one post. */
@Serializable
data class PostStats(
    @SerialName("post_id") val postId: String,
    val views: Int,
    @SerialName("views_by_day") val viewsByDay: List<DayCount>,
    val comments: Int,
    val likes: Int,
    val dislikes: Int,
    @SerialName("rating_count") val ratingCount: Int,
    @SerialName("rating_avg") val ratingAvg: Double,
    /** Index = stars (0-5), value = how many ratings gave that many stars. */
    @SerialName("rating_histogram") val ratingHistogram: List<Int>,
)

@Serializable
data class ErrorResponse(val error: String)

// --- API requests ---

@Serializable
data class NewComment(val body: String)

@Serializable
data class ReactionRequest(val value: Int)

@Serializable
data class RatingRequest(val stars: Int)

// --- Supabase (internal) ---

@Serializable
data class CommentInsert(
    @SerialName("post_id") val postId: String,
    @SerialName("author_id") val authorId: String,
    val body: String,
)

@Serializable
data class ReactionRow(
    @SerialName("post_id") val postId: String,
    @SerialName("user_id") val userId: String,
    val value: Int,
)

@Serializable
data class RatingRow(
    @SerialName("post_id") val postId: String,
    @SerialName("user_id") val userId: String,
    val stars: Int,
)

@Serializable
data class ViewInsert(
    @SerialName("post_id") val postId: String,
    @SerialName("viewer_id") val viewerId: String? = null,
)

@Serializable
data class CommentOwnerRow(@SerialName("author_id") val authorId: String)

@Serializable
data class StarsRow(val stars: Int)

@Serializable
data class ViewedAtRow(@SerialName("viewed_at") val viewedAt: String)

@Serializable
data class PostgrestError(val code: String? = null, val message: String? = null)

@Serializable
data class SupabaseUser(val id: String)
