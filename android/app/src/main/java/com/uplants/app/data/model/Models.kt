package com.uplants.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// JSON shapes returned by the services. Field names follow each service's README.

/** A user as embedded in posts, comments, search results, ... */
@Serializable
data class UserSummary(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
) {
    val shownName: String get() = displayName?.takeIf { it.isNotBlank() } ?: username
}

@Serializable
data class Category(
    val id: Int,
    val name: String,
    val slug: String,
    val icon: String? = null,
)

/** content_type values. */
object ContentTypes {
    const val PHOTO = "photo"
    const val VIDEO = "video"
    const val AUDIO = "audio"
    const val TEXT = "text"
}

@Serializable
data class Post(
    val id: String,
    val author: UserSummary? = null,
    val category: Category? = null,
    @SerialName("content_type") val contentType: String,
    /** "upload" (from the gallery) or "live" (recorded in the app). */
    val source: String = "upload",
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
) {
    val isLive: Boolean get() = source == "live"

    /** Image to show in a card: the thumbnail, or the photo itself. Videos without a thumbnail get none. */
    val previewImageUrl: String?
        get() = thumbnailUrl ?: mediaUrl.takeIf { contentType == ContentTypes.PHOTO }
}

@Serializable
data class PostPage(
    val posts: List<Post>,
    val limit: Int,
    val offset: Int,
    /** Only on `/feed`: false when the user has no follows/interests yet and gets popular posts. */
    val personalized: Boolean? = null,
)

@Serializable
data class Comment(
    val id: String,
    @SerialName("post_id") val postId: String,
    val author: UserSummary? = null,
    val body: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class CommentPage(val comments: List<Comment>, val limit: Int, val offset: Int)

/** What the logged-in user has done on a post: reaction 1 / -1 / null, stars 0-5 / null. */
@Serializable
data class MyInteractions(val reaction: Int? = null, val stars: Int? = null)

@Serializable
data class UserPage(val users: List<UserSummary>, val limit: Int, val offset: Int)

/** One of my favourite lists and whether a given post is in it. */
@Serializable
data class ListMembership(
    val id: String,
    val name: String,
    @SerialName("is_public") val isPublic: Boolean,
    @SerialName("contains_post") val containsPost: Boolean,
)

@Serializable
data class FavoriteList(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    @SerialName("is_public") val isPublic: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("item_count") val itemCount: Int = 0,
)

@Serializable
data class Message(
    val id: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("recipient_id") val recipientId: String,
    val body: String? = null,
    @SerialName("shared_post_id") val sharedPostId: String? = null,
    @SerialName("created_at") val createdAt: String,
)

/** Metadata of a new post (`POST /api/content/posts`, multipart part `metadata`). */
@Serializable
data class NewPost(
    @SerialName("category_id") val categoryId: Int,
    @SerialName("content_type") val contentType: String,
    /** "upload" (picked from the device) or "live" (recorded in the app; needs latitude/longitude). */
    val source: String,
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
