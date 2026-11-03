package com.uplants.interactions

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {

    private val post = "/api/interactions/posts/00000000-0000-0000-0000-000000000000"

    @Test
    fun `health check answers`() = testApplication {
        application { module() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("interactions-service"))
    }

    @Test
    fun `actions that need login refuse visitors`() = testApplication {
        application { module() }
        assertEquals(HttpStatusCode.Unauthorized, client.post("$post/comments").status)
        assertEquals(HttpStatusCode.Unauthorized, client.put("$post/reaction").status)
        assertEquals(HttpStatusCode.Unauthorized, client.put("$post/rating").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("$post/me").status)
    }

    @Test
    fun `bad paging is refused before calling the database`() = testApplication {
        application { module() }
        assertEquals(HttpStatusCode.BadRequest, client.get("$post/comments?limit=500").status)
    }
}
