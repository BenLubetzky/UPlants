package com.uplants.app.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe navigation destinations of the main app (after login or "continue as guest").

@Serializable
object FeedRoute

@Serializable
object PublishRoute

@Serializable
object AccountRoute

@Serializable
data class PostDetailRoute(val postId: String)

/** Key a detail screen sets on the previous back-stack entry when it deleted the post. */
const val DELETED_POST_ID_KEY = "deleted_post_id"

/** Key set on the feed's back-stack entry when a new post was published, so it reloads. */
const val FEED_NEEDS_REFRESH_KEY = "feed_needs_refresh"

/** Key a detail screen sets on the previous back-stack entry with the post it showed, so lists refresh its counters. */
const val VIEWED_POST_ID_KEY = "viewed_post_id"
