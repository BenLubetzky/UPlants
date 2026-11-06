package com.uplants.users.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- API responses ---

/** A user as shown in lists (search results, followers). */
@Serializable
data class ProfileSummary(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class UserPage(
    val users: List<ProfileSummary>,
    val limit: Int,
    val offset: Int,
)

/** Full profile. The user's posts come from content-service: `GET /api/content/posts?author={id}`. */
@Serializable
data class ProfileDto(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
    val bio: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("post_count") val postCount: Int = 0,
    @SerialName("follower_count") val followerCount: Int = 0,
    @SerialName("following_count") val followingCount: Int = 0,
    /** Whether the logged-in caller follows this user; null for visitors and on your own profile. */
    @SerialName("is_following") val isFollowing: Boolean? = null,
)

@Serializable
data class CategoryDto(
    val id: Int,
    val name: String,
    val slug: String,
    val icon: String? = null,
)

// --- API requests ---

/** Fields left out are not changed. An empty string clears the field. */
@Serializable
data class UpdateProfileRequest(
    @SerialName("display_name") val displayName: String? = null,
    val bio: String? = null,
)

@Serializable
data class InterestsRequest(
    @SerialName("category_ids") val categoryIds: List<Int>,
)

// --- Supabase rows ---

@Serializable
data class ProfileRow(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
    val bio: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("created_at") val createdAt: String,
)

/** A `follows` row with the other user embedded. */
@Serializable
data class FollowRow(val user: ProfileSummary? = null)

@Serializable
data class FollowInsert(
    @SerialName("follower_id") val followerId: String,
    @SerialName("following_id") val followingId: String,
)

@Serializable
data class InterestRow(val category: CategoryDto? = null)

@Serializable
data class InterestInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("category_id") val categoryId: Int,
)
