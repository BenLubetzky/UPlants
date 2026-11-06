package com.uplants.content

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {

    @Test
    fun `health check answers`() = testApplication {
        application { module() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("content-service"))
    }

    @Test
    fun `publishing without a token is refused`() = testApplication {
        application { module() }
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/content/posts").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/content/feed").status)
    }

    @Test
    fun `bad feed parameters are refused before calling the database`() = testApplication {
        application { module() }
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/content/posts?sort=random").status)
    }
}
