package com.uplants.users.service

import com.uplants.users.models.CategoryDto
import com.uplants.users.models.FollowInsert
import com.uplants.users.models.FollowRow
import com.uplants.users.models.InterestInsert
import com.uplants.users.models.InterestRow
import com.uplants.users.models.ProfileDto
import com.uplants.users.models.ProfileRow
import com.uplants.users.models.ProfileSummary
import com.uplants.users.models.UserPage
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Profiles, search, follows and interests. Owns `profiles`, `follows`, `user_interests`. */
class ProfilesService(private val supabase: SupabaseClient) {

    private companion object {
        const val SUMMARY_SELECT = "id,username,display_name,avatar_url"
        const val PROFILE_SELECT = "id,username,display_name,bio,avatar_url,created_at"
        const val SINGLE_OBJECT = "application/vnd.pgrst.object+json"
    }

    private val unknownCategory = ServiceResult.Fail(HttpStatusCode.BadRequest, "Unknown category")

    // --- Search ---

    /** Users whose username or display name contains [query] (case-insensitive). `query` must be validated. */
    suspend fun search(query: String, limit: Int, offset: Int): ServiceResult<UserPage> = with(supabase) {
        val response = http.get("$restUrl/profiles") {
            asService()
            parameter("select", SUMMARY_SELECT)
            parameter("or", searchFilter(query))
            parameter("order", "username.asc")
            parameter("limit", limit)
            parameter("offset", offset)
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        ServiceResult.Ok(UserPage(response.body(), limit, offset))
    }

    // --- Profiles ---

    /** Profile with counters. [viewerId] (the caller, if logged in) fills `is_following`. */
    suspend fun profile(id: String, viewerId: String?): ServiceResult<ProfileDto> = with(supabase) {
        if (!isUuid(id)) return USER_NOT_FOUND
        coroutineScope {
            val row = async { profileRow(id) }
            val posts = async { count("posts", "author_id" to "eq.$id") }
            val followers = async { count("follows", "following_id" to "eq.$id") }
            val following = async { count("follows", "follower_id" to "eq.$id") }
            val isFollowing = async {
                if (viewerId == null || viewerId == id) null
                else (count("follows", "follower_id" to "eq.$viewerId", "following_id" to "eq.$id") ?: 0) > 0
            }

            val profile = row.await() ?: return@coroutineScope USER_NOT_FOUND
            ServiceResult.Ok(
                ProfileDto(
                    id = profile.id,
                    username = profile.username,
                    displayName = profile.displayName,
                    bio = profile.bio,
                    avatarUrl = profile.avatarUrl,
                    createdAt = profile.createdAt,
                    postCount = posts.await() ?: 0,
                    followerCount = followers.await() ?: 0,
                    followingCount = following.await() ?: 0,
                    isFollowing = isFollowing.await(),
                )
            )
        }
    }

    /** Changes display name and/or bio. [changes] must be validated (see validateProfileUpdate). */
    suspend fun updateProfile(userId: String, changes: Map<String, String?>): ServiceResult<ProfileDto> = with(supabase) {
        val response = http.patch("$restUrl/profiles") {
            asService()
            header("Prefer", "return=minimal")
            parameter("id", "eq.$userId")
            contentType(ContentType.Application.Json)
            setBody(changes)
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        profile(userId, viewerId = null)
    }

    private suspend fun profileRow(id: String): ProfileRow? = with(supabase) {
        val response = http.get("$restUrl/profiles") {
            asService()
            header("Accept", SINGLE_OBJECT)
            parameter("select", PROFILE_SELECT)
            parameter("id", "eq.$id")
        }
        // With the single-object header, "no rows" comes back as 406.
        if (response.status == HttpStatusCode.NotAcceptable) return null
        if (!response.status.isSuccess()) error("Supabase ${response.status}")
        response.body()
    }

    // --- Follows ---

    /** Following twice is fine: the second time does nothing (and sends no second notification). */
    suspend fun follow(followerId: String, followingId: String): ServiceResult<Unit> = with(supabase) {
        if (!isUuid(followingId)) return USER_NOT_FOUND
        if (followerId == followingId) return ServiceResult.Fail(HttpStatusCode.BadRequest, "You can't follow yourself")
        val response = http.post("$restUrl/follows") {
            asService()
            parameter("on_conflict", "follower_id,following_id")
            header("Prefer", "resolution=ignore-duplicates,return=minimal")
            contentType(ContentType.Application.Json)
            setBody(FollowInsert(followerId, followingId))
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        ServiceResult.Ok(Unit, HttpStatusCode.NoContent)
    }

    suspend fun unfollow(followerId: String, followingId: String): ServiceResult<Unit> = with(supabase) {
        if (!isUuid(followingId)) return USER_NOT_FOUND
        val response = http.delete("$restUrl/follows") {
            asService()
            parameter("follower_id", "eq.$followerId")
            parameter("following_id", "eq.$followingId")
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        ServiceResult.Ok(Unit, HttpStatusCode.NoContent)
    }

    /** Who follows [userId], newest first. */
    suspend fun followers(userId: String, limit: Int, offset: Int) =
        followList(userId, limit, offset, filterColumn = "following_id", embed = "follows_follower_id_fkey")

    /** Who [userId] follows, newest first. */
    suspend fun following(userId: String, limit: Int, offset: Int) =
        followList(userId, limit, offset, filterColumn = "follower_id", embed = "follows_following_id_fkey")

    private suspend fun followList(
        userId: String, limit: Int, offset: Int, filterColumn: String, embed: String,
    ): ServiceResult<UserPage> = with(supabase) {
        if (!isUuid(userId)) return USER_NOT_FOUND
        val response = http.get("$restUrl/follows") {
            asService()
            parameter("select", "user:profiles!$embed($SUMMARY_SELECT)")
            parameter(filterColumn, "eq.$userId")
            parameter("order", "created_at.desc")
            parameter("limit", limit)
            parameter("offset", offset)
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        val users: List<ProfileSummary> = response.body<List<FollowRow>>().mapNotNull { it.user }
        ServiceResult.Ok(UserPage(users, limit, offset))
    }

    // --- Interests (categories that shape the personalised feed) ---

    suspend fun interests(userId: String): ServiceResult<List<CategoryDto>> = with(supabase) {
        val response = http.get("$restUrl/user_interests") {
            asService()
            parameter("select", "category:categories!user_interests_category_id_fkey(id,name,slug,icon)")
            parameter("user_id", "eq.$userId")
        }
        if (!response.status.isSuccess()) return upstreamError(response)
        ServiceResult.Ok(response.body<List<InterestRow>>().mapNotNull { it.category }.sortedBy { it.name })
    }

    /** Replaces the user's interests with [categoryIds] (validated). Returns the new list. */
    suspend fun setInterests(userId: String, categoryIds: List<Int>): ServiceResult<List<CategoryDto>> = with(supabase) {
        // Add the new ones first, then drop the rest, so the list is never empty halfway through.
        if (categoryIds.isNotEmpty()) {
            val insert = http.post("$restUrl/user_interests") {
                asService()
                parameter("on_conflict", "user_id,category_id")
                header("Prefer", "resolution=ignore-duplicates,return=minimal")
                contentType(ContentType.Application.Json)
                setBody(categoryIds.map { InterestInsert(userId, it) })
            }
            if (!insert.status.isSuccess()) return upstreamError(insert, missingRow = unknownCategory)
        }
        val remove = http.delete("$restUrl/user_interests") {
            asService()
            parameter("user_id", "eq.$userId")
            if (categoryIds.isNotEmpty()) parameter("category_id", "not.in.(${categoryIds.joinToString(",")})")
        }
        if (!remove.status.isSuccess()) return upstreamError(remove)
        interests(userId)
    }
}
