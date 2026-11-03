package com.uplants.content.plugins

import com.uplants.content.models.ErrorResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.channels.UnresolvedAddressException

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            encodeDefaults = true // always send every field, even 0 / null / []
            ignoreUnknownKeys = true
        })
    }
}

fun Application.configureCORS() {
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Delete)
    }
}

/** Graceful degradation: if Supabase can't be reached, say so with 503 instead of crashing. */
fun Application.configureErrors() {
    install(StatusPages) {
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request"))
        }
        exception<IOException> { call, cause ->
            call.application.log.warn("Supabase unreachable: ${cause.message}")
            call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Content is temporarily unavailable"))
        }
        exception<UnresolvedAddressException> { call, _ ->
            call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Content is temporarily unavailable"))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unexpected error", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Server error"))
        }
    }
}
