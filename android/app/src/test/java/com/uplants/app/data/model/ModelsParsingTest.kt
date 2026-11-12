package com.uplants.app.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The example answers from each service's README must decode into the app's models. */
class ModelsParsingTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `content-service post page decodes`() {
        val page = json.decodeFromString<PostPage>(
            """
            {"posts":[{"id":"ea8960e4","author":{"id":"5e36","username":"aly_planta","display_name":"Aly","avatar_url":null},
             "category":{"id":1,"name":"Houseplants","slug":"houseplants","icon":"potted_plant"},
             "content_type":"photo","source":"live","title":"My fern","description":null,"plant_species":null,
             "tags":["fern","indoor"],"media_url":"https://x.supabase.co/storage/v1/object/public/post-media/a.png",
             "thumbnail_url":null,"duration_seconds":null,"latitude":41.1496,"longitude":-8.611,"location_name":"Porto",
             "sensor_data":{"accelerometer":[0.1,9.8,0.2]},
             "view_count":0,"like_count":0,"dislike_count":0,"comment_count":0,"share_count":0,
             "rating_avg":0.0,"rating_count":0,"interaction_count":0,
             "created_at":"2026-11-03T16:50:12.345+00:00","updated_at":"2026-11-03T16:50:12.345+00:00"}],
             "limit":20,"offset":0}
            """
        )
        val post = page.posts.single()
        assertEquals("Aly", post.author?.shownName)
        assertTrue(post.isLive)
        assertEquals(post.mediaUrl, post.previewImageUrl)
        assertEquals(41.1496, post.latitude!!, 0.0)
        assertNull(page.personalized)
    }

    @Test
    fun `feed page has personalized flag`() {
        val page = json.decodeFromString<PostPage>("""{"posts":[],"limit":20,"offset":0,"personalized":false}""")
        assertEquals(false, page.personalized)
    }

    @Test
    fun `interactions comments and my state decode`() {
        val page = json.decodeFromString<CommentPage>(
            """{"comments":[{"id":"8c1e","post_id":"2f09","author":{"id":"5e36","username":"aly_planta","display_name":"Aly","avatar_url":null},
               "body":"Nice fern!","created_at":"2026-11-03T17:20:00.123+00:00"}],"limit":20,"offset":0}"""
        )
        assertEquals("Nice fern!", page.comments.single().body)
        val mine = json.decodeFromString<MyInteractions>("""{"reaction":-1,"stars":4}""")
        assertEquals(-1, mine.reaction)
        assertEquals(4, mine.stars)
        assertNull(json.decodeFromString<MyInteractions>("""{"reaction":null,"stars":null}""").stars)
    }

    @Test
    fun `favorites membership decodes`() {
        val lists = json.decodeFromString<List<ListMembership>>("""[{"id":"l1","name":"My ferns","is_public":false,"contains_post":true}]""")
        assertTrue(lists.single().containsPost)
        assertFalse(lists.single().isPublic)
    }

    @Test
    fun `users search page decodes`() {
        val page = json.decodeFromString<UserPage>("""{"users":[{"id":"u1","username":"ben","display_name":null,"avatar_url":null}],"limit":20,"offset":0}""")
        assertEquals("ben", page.users.single().shownName)
    }

    @Test
    fun `favorites list detail decodes`() {
        val detail = json.decodeFromString<FavoriteListDetail>(
            """{"id":"l1","name":"My ferns","is_public":false,"created_at":"2026-11-03T17:20:00+00:00","item_count":1,
               "owner":{"id":"u1","username":"ana","display_name":"Ana","avatar_url":null},
               "posts":[{"added_at":"2026-11-03T17:20:00+00:00","post":{"id":"p1","title":"Boston fern","content_type":"photo",
                 "media_url":"https://x/a.png","thumbnail_url":null,"rating_avg":4.5,"like_count":3,"comment_count":1,
                 "created_at":"2026-11-03T17:20:00+00:00","author":{"id":"u2","username":"ben","display_name":null,"avatar_url":null},
                 "category":{"id":1,"name":"Houseplants","slug":"houseplants","icon":"potted_plant"}}}],
               "limit":20,"offset":0}"""
        )
        assertEquals("Boston fern", detail.posts.single().post.title)
        assertEquals("https://x/a.png", detail.posts.single().post.previewImageUrl)
    }

    @Test
    fun `messaging inbox and thread decode`() {
        val message = """{"id":"m1","sender_id":"u1","recipient_id":"u2","body":"Look at this one!","shared_post_id":"p1",
            "read_at":null,"created_at":"2026-11-03T17:20:00+00:00",
            "sender":{"id":"u1","username":"ana","display_name":"Ana","avatar_url":null},
            "recipient":{"id":"u2","username":"ben","display_name":null,"avatar_url":null},
            "shared_post":{"id":"p1","title":"Boston fern","content_type":"photo","media_url":"https://x/a.png","thumbnail_url":null,
              "author":{"id":"u2","username":"ben","display_name":null,"avatar_url":null}}}"""
        val inbox = json.decodeFromString<List<Conversation>>("""[{"user":{"id":"u1","username":"ana"},"last_message":$message,"unread_count":2}]""")
        assertEquals(2, inbox.single().unreadCount)
        assertEquals("Boston fern", inbox.single().lastMessage.sharedPost?.title)
        val page = json.decodeFromString<MessagePage>("""{"messages":[$message],"limit":20,"offset":0}""")
        assertNull(page.messages.single().readAt)
        assertEquals(3, json.decodeFromString<UnreadCount>("""{"unread":3}""").unread)
    }

    @Test
    fun `video without thumbnail has no preview image`() {
        val post = Post(id = "1", contentType = ContentTypes.VIDEO, title = "t", mediaUrl = "v.mp4", createdAt = "")
        assertNull(post.previewImageUrl)
    }
}
