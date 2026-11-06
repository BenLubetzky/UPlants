package com.uplants.content.service

import com.uplants.content.models.PostDto
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.ln
import kotlin.math.min

/** Personalised feed ranking. No I/O, so it's easy to unit test. */

/** What we know about the reader: who they follow and which categories they like. */
data class FeedSignals(
    val followedAuthors: Set<String>,
    val interestCategories: Set<Int>,
) {
    val isEmpty get() = followedAuthors.isEmpty() && interestCategories.isEmpty()
}

const val FOLLOWED_AUTHOR_BONUS = 3.0
const val INTEREST_BONUS = 2.0
const val RATING_WEIGHT = 0.4      // 5 stars -> +2
const val INTERACTION_WEIGHT = 0.5 // per log(1 + interactions)
const val AGE_PENALTY_PER_DAY = 0.1
const val MAX_AGE_PENALTY = 3.0    // after 30 days, age stops mattering

/** Higher = shown first. Followed authors and favourite categories weigh most, then quality, then freshness. */
fun feedScore(post: PostDto, signals: FeedSignals, now: Instant): Double {
    var score = 0.0
    if (post.author?.id in signals.followedAuthors) score += FOLLOWED_AUTHOR_BONUS
    if (post.category?.id in signals.interestCategories) score += INTEREST_BONUS
    score += post.ratingAvg * RATING_WEIGHT
    score += ln(1.0 + post.interactionCount.coerceAtLeast(0)) * INTERACTION_WEIGHT
    score -= min(ageInDays(post.createdAt, now) * AGE_PENALTY_PER_DAY, MAX_AGE_PENALTY)
    return score
}

/** Sorts by score (newest first on ties) and drops duplicates. */
fun rankFeed(posts: List<PostDto>, signals: FeedSignals, now: Instant): List<PostDto> =
    posts.distinctBy { it.id }
        .map { it to feedScore(it, signals, now) }
        .sortedWith(compareByDescending<Pair<PostDto, Double>> { it.second }.thenByDescending { it.first.createdAt })
        .map { it.first }

private fun ageInDays(createdAt: String, now: Instant): Double {
    val created = runCatching { OffsetDateTime.parse(createdAt).toInstant() }.getOrNull() ?: return 0.0
    return Duration.between(created, now).toMinutes().coerceAtLeast(0) / (60.0 * 24)
}

/** The PostgREST `or=(...)` filter for posts by followed authors or in interest categories. Null if there's nothing to match. */
fun feedCandidateFilter(signals: FeedSignals): String? {
    val parts = buildList {
        if (signals.followedAuthors.isNotEmpty()) add("author_id.in.(${signals.followedAuthors.joinToString(",")})")
        if (signals.interestCategories.isNotEmpty()) add("category_id.in.(${signals.interestCategories.joinToString(",")})")
    }
    return if (parts.isEmpty()) null else parts.joinToString(",", "(", ")")
}

/** How many posts are ranked at most: the feed pages through these. */
const val FEED_CANDIDATES = 200

/** Popular posts mixed in, so a small network still gives a full feed. */
const val FEED_POPULAR = 50

/** Followed authors used for the feed (keeps the request URL short). */
const val FEED_MAX_FOLLOWS = 200
