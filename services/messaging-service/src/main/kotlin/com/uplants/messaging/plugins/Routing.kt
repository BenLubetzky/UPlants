package com.uplants.messaging.plugins

import com.uplants.messaging.models.ErrorResponse
import com.uplants.messaging.models.NewMessageRequest
import com.uplants.messaging.service.AuthVerifier
import com.uplants.messaging.service.Checked
import com.uplants.messaging.service.MessagingService
import com.uplants.messaging.service.ServiceResult
import com.uplants.messaging.service.parsePage
import com.uplants.messaging.service.validateNewMessage
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

/** Every messaging endpoint needs a logged-in user. */
fun Application.configureRouting(service: MessagingService, auth: AuthVerifier) {
    routing {
        get("/health") { call.respond(mapOf("status" to "ok", "service" to "messaging-service")) }

        route("/api/messages") {
            get("/health") { call.respond(mapOf("status" to "ok", "service" to "messaging-service")) }

            // Send a message and/or share a post
            post {
                val userId = call.requireUser(auth) ?: return@post
                when (val message = validateNewMessage(userId, call.receive<NewMessageRequest>())) {
                    is Checked.Invalid -> call.respondError(HttpStatusCode.BadRequest, message.message)
                    is Checked.Valid -> call.respondResult(service.send(message.value))
                }
            }

            get("/conversations") {
                val userId = call.requireUser(auth) ?: return@get
                call.respondResult(service.conversations(userId))
            }

            get("/unread-count") {
                val userId = call.requireUser(auth) ?: return@get
                call.respondResult(service.unreadCount(userId))
            }

            route("/with/{userId}") {
                get {
                    val userId = call.requireUser(auth) ?: return@get
                    when (val page = parsePage(call.request.queryParameters["limit"], call.request.queryParameters["offset"])) {
                        is Checked.Invalid -> call.respondError(HttpStatusCode.BadRequest, page.message)
                        is Checked.Valid -> call.respondResult(service.thread(userId, call.otherUser, page.value.first, page.value.second))
                    }
                }
                post("/read") {
                    val userId = call.requireUser(auth) ?: return@post
                    call.respondResult(service.markRead(userId, call.otherUser))
                }
            }

            delete("/{messageId}") {
                val userId = call.requireUser(auth) ?: return@delete
                call.respondResult(service.delete(userId, call.parameters["messageId"]!!))
            }
        }
    }
}

private val ApplicationCall.otherUser get() = parameters["userId"]!!

/** The logged-in user's id, or null after answering 401. */
private suspend fun ApplicationCall.requireUser(auth: AuthVerifier): String? {
    val token = request.headers["Authorization"]
        ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
        ?.substring(7)?.trim()
        ?.takeIf { it.isNotEmpty() }
    if (token == null) {
        respondError(HttpStatusCode.Unauthorized, "Log in first")
        return null
    }
    val userId = auth.userIdFor(token)
    if (userId == null) respondError(HttpStatusCode.Unauthorized, "Session expired, log in again")
    return userId
}

private suspend inline fun <reified T : Any> ApplicationCall.respondResult(result: ServiceResult<T>) = when (result) {
    is ServiceResult.Ok -> if (result.value is Unit) respond(result.status) else respond(result.status, result.value)
    is ServiceResult.Fail -> respondError(result.status, result.message)
}

private suspend fun ApplicationCall.respondError(status: HttpStatusCode, message: String) =
    respond(status, ErrorResponse(message))
