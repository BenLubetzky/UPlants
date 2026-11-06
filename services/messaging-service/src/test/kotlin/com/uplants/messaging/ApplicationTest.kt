package com.uplants.messaging

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Only checks that answer before calling Supabase, so no database is needed. */
class ApplicationTest {

    private val other = "00000000-0000-0000-0000-000000000000"

    @Test
    fun `health check answers`() = testApplication {
        application { module() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("messaging-service"))
    }

    @Test
    fun `every endpoint needs login`() = testApplication {
        application { module() }
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/messages").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/messages/conversations").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/messages/unread-count").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/messages/with/$other").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/messages/with/$other/read").status)
        assertEquals(HttpStatusCode.Unauthorized, client.delete("/api/messages/$other").status)
    }
}
