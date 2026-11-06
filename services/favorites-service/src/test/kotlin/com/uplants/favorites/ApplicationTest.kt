package com.uplants.favorites

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Only checks that answer before calling Supabase, so no database is needed. */
class ApplicationTest {

    private val list = "/api/favorites/lists/00000000-0000-0000-0000-000000000000"
    private val post = "00000000-0000-0000-0000-000000000001"

    @Test
    fun `health check answers`() = testApplication {
        application { module() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("favorites-service"))
    }

    @Test
    fun `actions that need login refuse visitors`() = testApplication {
        application { module() }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/favorites/lists").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/favorites/lists").status)
        assertEquals(HttpStatusCode.Unauthorized, client.patch(list).status)
        assertEquals(HttpStatusCode.Unauthorized, client.delete(list).status)
        assertEquals(HttpStatusCode.Unauthorized, client.put("$list/posts/$post").status)
        assertEquals(HttpStatusCode.Unauthorized, client.delete("$list/posts/$post").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/favorites/posts/$post/lists").status)
    }

    @Test
    fun `bad input is refused before calling the database`() = testApplication {
        application { module() }
        assertEquals(HttpStatusCode.BadRequest, client.get("$list?limit=500").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/favorites/lists/not-a-uuid").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/favorites/users/not-a-uuid/lists").status)
    }
}
