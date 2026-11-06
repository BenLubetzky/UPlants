package com.uplants.users

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Only checks that answer before calling Supabase, so no database is needed. */
class ApplicationTest {

    private val user = "/api/users/00000000-0000-0000-0000-000000000000"

    @Test
    fun `health check answers`() = testApplication {
        application { module() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("users-service"))
    }

    @Test
    fun `actions that need login refuse visitors`() = testApplication {
        application { module() }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/users/me").status)
        assertEquals(HttpStatusCode.Unauthorized, client.patch("/api/users/me").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/users/me/interests").status)
        assertEquals(HttpStatusCode.Unauthorized, client.put("/api/users/me/interests").status)
        assertEquals(HttpStatusCode.Unauthorized, client.put("$user/follow").status)
        assertEquals(HttpStatusCode.Unauthorized, client.delete("$user/follow").status)
    }

    @Test
    fun `bad input is refused before calling the database`() = testApplication {
        application { module() }
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/users/search").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/users/search?q=fern&limit=500").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("$user/followers?offset=-1").status)
    }

    @Test
    fun `an id that is not a uuid is not found`() = testApplication {
        application { module() }
        assertEquals(HttpStatusCode.NotFound, client.get("/api/users/not-a-uuid").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/users/not-a-uuid/followers").status)
    }
}
