package com.uplants.app.data.notifications

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.uplants.app.data.auth.SessionManager
import com.uplants.app.data.content.ContentRepository
import com.uplants.app.data.content.PostFilter
import com.uplants.app.data.content.PostSort
import com.uplants.app.data.messaging.MessagingRepository
import com.uplants.app.data.users.UsersRepository
import kotlinx.coroutines.flow.first
import java.time.OffsetDateTime

/** Where tapping a notification takes the user. */
sealed interface DeepLink {
    data class Post(val postId: String) : DeepLink
    data class Chat(val userId: String, val name: String) : DeepLink
    data class Profile(val userId: String) : DeepLink
}

/** A notification to show, with a stable [key] so a newer one for the same thing replaces the old one. */
data class AppNotification(val key: String, val title: String, val text: String, val link: DeepLink)

private val Context.notificationStateStore by preferencesDataStore(name = "notification_state")

/**
 * Requirement 21 without a notifications-service: works out what is new since the last check by comparing
 * with what was seen last time (kept in DataStore), using the existing services:
 * - new messages (messaging-service, unread count per conversation),
 * - new followers (users-service),
 * - new posts by people you follow (users-service + content-service),
 * - new likes / comments on your posts (content-service counters).
 * The first check for an account only records the current state, so nothing old is announced.
 */
class ActivityChecker(
    context: Context,
    private val sessions: SessionManager,
    private val content: ContentRepository,
    private val users: UsersRepository,
    private val messaging: MessagingRepository,
) {
    private val store = context.applicationContext.notificationStateStore

    /** Null when nobody is logged in. Failures of one service only skip that kind of notification. */
    suspend fun check(): List<AppNotification> {
        val me = sessions.current() ?: sessions.stored.first() ?: return emptyList()
        val prefs = store.data.first()
        val firstRun = prefs[OWNER] != me.userId
        val out = mutableListOf<AppNotification>()
        val updates = mutableMapOf<Preferences.Key<Set<String>>, Set<String>>()
        var lastPostCheck: String? = null

        // New messages: a conversation whose unread count went up.
        messaging.conversations().onSuccess { conversations ->
            val before = if (firstRun) emptyMap() else decodeCounts(prefs[UNREAD])
            conversations.forEach { c ->
                if (!firstRun && c.unreadCount > (before[c.user.id] ?: 0)) {
                    val preview = c.lastMessage.body?.takeIf { it.isNotBlank() }
                        ?: c.lastMessage.sharedPost?.let { "📎 ${it.title}" }.orEmpty()
                    out += AppNotification("msg-${c.user.id}", "${c.user.shownName}", preview, DeepLink.Chat(c.user.id, c.user.shownName))
                }
            }
            updates[UNREAD] = conversations.map { "${it.user.id}:${it.unreadCount}" }.toSet()
        }

        // New followers.
        users.followers(me.userId, 50, 0).onSuccess { page ->
            val known = prefs[FOLLOWERS].orEmpty()
            if (!firstRun) {
                page.users.filter { it.id !in known }.forEach { u ->
                    out += AppNotification("follow-${u.id}", "@${u.username}", FOLLOW_TEXT, DeepLink.Profile(u.id))
                }
            }
            updates[FOLLOWERS] = known + page.users.map { it.id }
        }

        // New posts by people I follow (the "availability of new contents" the brief mentions).
        val following = users.following(me.userId, 50, 0).getOrNull()?.users?.associateBy { it.id }.orEmpty()
        content.posts(PostFilter(sort = PostSort.RECENT), 30, 0).onSuccess { page ->
            val since = prefs[LAST_POST_CHECK]?.let(::parseTime)
            if (!firstRun && since != null) {
                page.posts
                    .filter { it.author?.id in following && (parseTime(it.createdAt)?.isAfter(since) == true) }
                    .take(MAX_POST_NOTIFICATIONS)
                    .forEach { p ->
                        val who = p.author?.shownName ?: ""
                        out += AppNotification("post-${p.id}", "$who · $NEW_POST_TEXT", p.title, DeepLink.Post(p.id))
                    }
            }
            lastPostCheck = page.posts.mapNotNull { parseTime(it.createdAt) }.maxOrNull()?.toString()
                ?: prefs[LAST_POST_CHECK] ?: OffsetDateTime.now().toString()
        }

        // Likes and comments on my posts: compare the counters with last time.
        content.posts(PostFilter(authorId = me.userId, sort = PostSort.RECENT), 50, 0).onSuccess { page ->
            val before = decodePostCounters(prefs[MY_POSTS])
            page.posts.forEach { p ->
                val old = before[p.id] ?: return@forEach
                val newComments = p.commentCount - old.first
                val newLikes = p.likeCount - old.second
                if (!firstRun && (newComments > 0 || newLikes > 0)) {
                    out += AppNotification("activity-${p.id}", p.title, activityText(newComments, newLikes), DeepLink.Post(p.id))
                }
            }
            updates[MY_POSTS] = page.posts.map { "${it.id}:${it.commentCount}:${it.likeCount}" }.toSet()
        }

        store.edit { e ->
            e[OWNER] = me.userId
            updates.forEach { (k, v) -> e[k] = v }
            lastPostCheck?.let { e[LAST_POST_CHECK] = it }
        }
        return out
    }

    /** Forget everything (e.g. on log out), so the next account starts with a fresh baseline. */
    suspend fun reset() {
        store.edit { it.clear() }
    }

    private companion object {
        val OWNER = stringPreferencesKey("owner")
        val LAST_POST_CHECK = stringPreferencesKey("last_post_check")
        val FOLLOWERS = stringSetPreferencesKey("followers")
        val UNREAD = stringSetPreferencesKey("unread")
        val MY_POSTS = stringSetPreferencesKey("my_posts")
        const val MAX_POST_NOTIFICATIONS = 3

        // Plain text keeps this class free of Android resources; the strings are short and English like the app.
        const val FOLLOW_TEXT = "started following you"
        const val NEW_POST_TEXT = "new post"
    }
}

internal fun activityText(newComments: Int, newLikes: Int): String = listOfNotNull(
    newComments.takeIf { it > 0 }?.let { if (it == 1) "1 new comment" else "$it new comments" },
    newLikes.takeIf { it > 0 }?.let { if (it == 1) "1 new like" else "$it new likes" },
).joinToString(" and ")

/** "userId:3" entries → map. */
internal fun decodeCounts(entries: Set<String>?): Map<String, Int> =
    entries.orEmpty().mapNotNull { e ->
        val i = e.lastIndexOf(':')
        if (i <= 0) null else e.substring(0, i) to (e.substring(i + 1).toIntOrNull() ?: 0)
    }.toMap()

/** "postId:comments:likes" entries → map of (comments, likes). */
internal fun decodePostCounters(entries: Set<String>?): Map<String, Pair<Int, Int>> =
    entries.orEmpty().mapNotNull { e ->
        val parts = e.split(':')
        if (parts.size != 3) null else parts[0] to ((parts[1].toIntOrNull() ?: 0) to (parts[2].toIntOrNull() ?: 0))
    }.toMap()

private fun parseTime(iso: String): OffsetDateTime? = runCatching { OffsetDateTime.parse(iso) }.getOrNull()
