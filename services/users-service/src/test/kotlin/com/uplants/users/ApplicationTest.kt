package com.uplants.users

import com.uplants.users.plugins.configureRouting
import com.uplants.users.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {

    @Test
    fun testHealthCheck() = testApplication {
        application {
            configureSerialization()
            configureRouting()
        }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("users-service"))
    }
}
