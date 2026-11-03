package com.uplants.interactions.plugins

import com.uplants.interactions.models.ErrorResponse
import com.uplants.interactions.models.NewComment
import com.uplants.interactions.models.RatingRequest
import com.uplants.interactions.models.ReactionRequest
import com.uplants.interactions.service.AuthVerifier
import com.uplants.interactions.service.Checked
import com.uplants.interactions.service.InteractionsService
import com.uplants.interactions.service.ServiceResult
import com.uplants.interactions.service.parsePage
import com.uplants.interactions.service.validateComment
import com.uplants.interactions.service.validateReaction
import com.uplants.interactions.service.validateStars
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting(service: InteractionsService, auth: AuthVerifier) {
    routing {
        get("/health") { call.respond(mapOf("status" to "ok", "service" to "interactions-service")) }

        route("/api/interactions") {
            get("/health") { call.respond(mapOf("status" to "ok", "service" to "interactions-service")) }

            route("/posts/{postId}") {
                // Comments
                get("/comments") {
                    when (val page = parsePage(call.request.queryParameters["limit"], call.request.queryParameters["offset"])) {
                        is Checked.Invalid -> call.respondError(HttpStatusCode.BadRequest, page.message)
                        is Checked.Valid -> call.respondResult(service.listComments(call.postId, page.value.first, page.value.second))
                    }
                }
                post("/comments") {
                    val userId = call.requireUser(auth) ?: return@post
                    when (val body = validateComment(call.receive<NewComment>().body)) {
                        is Checked.Invalid -> call.respondError(HttpStatusCode.BadRequest, body.message)
                        is Checked.Valid -> call.respondResult(service.addComment(userId, call.postId, body.value))
                    }
                }

                // Like (1) / dislike (-1)
                put("/reaction") {
                    val userId = call.requireUser(auth) ?: return@put
                    when (val value = validateReaction(call.receive<ReactionRequest>().value)) {
                        is Checked.Invalid -> call.respondError(HttpStatusCode.BadRequest, value.message)
                        is Checked.Valid -> call.respondResult(service.setReaction(userId, call.postId, value.value))
                    }
                }
                delete("/reaction") {
                    val userId = call.requireUser(auth) ?: return@delete
                    call.respondResult(service.removeReaction(userId, call.postId))
                }

                // Stars 0-5
                put("/rating") {
                    val userId = call.requireUser(auth) ?: return@put
                    when (val stars = validateStars(call.receive<RatingRequest>().stars)) {
                        is Checked.Invalid -> call.respondError(HttpStatusCode.BadRequest, stars.message)
                        is Checked.Valid -> call.respondResult(service.setRating(userId, call.postId, stars.value))
                    }
                }
                delete("/rating") {
                    val userId = call.requireUser(auth) ?: return@delete
                    call.respondResult(service.removeRating(userId, call.postId))
                }

                // The logged-in user's own like/dislike and stars
                get("/me") {
                    val userId = call.requireUser(auth) ?: return@get
                    call.respondResult(service.myInteractions(userId, call.postId))
                }

                // Views: visitors too; the token is optional
                post("/views") { call.respondResult(service.recordView(call.optionalUser(auth), call.postId)) }

                // Dashboard
                get("/stats") { call.respondResult(service.postStats(call.postId)) }
            }

            delete("/comments/{commentId}") {
                val userId = call.requireUser(auth) ?: return@delete
                call.respondResult(service.deleteComment(userId, call.parameters["commentId"]!!))
            }
        }
    }
}

private val ApplicationCall.postId get() = parameters["postId"]!!

private fun ApplicationCall.bearerToken(): String? =
    request.headers["Authorization"]
        ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
        ?.substring(7)?.trim()
        ?.takeIf { it.isNotEmpty() }

/** The logged-in user's id, or null after answering 401. */
private suspend fun ApplicationCall.requireUser(auth: AuthVerifier): String? {
    val token = bearerToken()
    if (token == null) {
        respondError(HttpStatusCode.Unauthorized, "Log in first")
        return null
    }
    val userId = auth.userIdFor(token)
    if (userId == null) respondError(HttpStatusCode.Unauthorized, "Session expired, log in again")
    return userId
}

/** The user's id if a valid token was sent, otherwise null (visitor). */
private suspend fun ApplicationCall.optionalUser(auth: AuthVerifier): String? = bearerToken()?.let { auth.userIdFor(it) }

private suspend inline fun <reified T : Any> ApplicationCall.respondResult(result: ServiceResult<T>) = when (result) {
    is ServiceResult.Ok -> if (result.value is Unit) respond(result.status) else respond(result.status, result.value)
    is ServiceResult.Fail -> respondError(result.status, result.message)
}

private suspend fun ApplicationCall.respondError(status: HttpStatusCode, message: String) =
    respond(status, ErrorResponse(message))
