package com.uplants.users.plugins

import com.uplants.users.models.ErrorResponse
import com.uplants.users.models.LoginRequest
import com.uplants.users.models.RegisterRequest
import com.uplants.users.service.AuthResult
import com.uplants.users.service.SupabaseAuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting(authService: SupabaseAuthService = SupabaseAuthService()) {
    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok", "service" to "users-service"))
        }

        route("/api/users") {
            get("/health") {
                call.respond(mapOf("status" to "ok", "service" to "users-service"))
            }

            post("/login") {
                try {
                    val request = call.receive<LoginRequest>()
                    when (val result = authService.login(request)) {
                        is AuthResult.Success -> call.respond(HttpStatusCode.OK, result.data)
                        is AuthResult.Error -> call.respond(result.status, ErrorResponse(result.message))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
                }
            }

            post("/register") {
                try {
                    val request = call.receive<RegisterRequest>()
                    when (val result = authService.register(request)) {
                        is AuthResult.Success -> call.respond(HttpStatusCode.Created, result.data)
                        is AuthResult.Error -> call.respond(result.status, ErrorResponse(result.message))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
                }
            }
        }
    }
}
