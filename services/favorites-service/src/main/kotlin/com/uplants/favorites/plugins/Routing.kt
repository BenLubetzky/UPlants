package com.uplants.favorites.plugins

import com.uplants.favorites.models.ErrorResponse
import com.uplants.favorites.models.NewListRequest
import com.uplants.favorites.models.UpdateListRequest
import com.uplants.favorites.service.AuthVerifier
import com.uplants.favorites.service.Checked
import com.uplants.favorites.service.FavoritesService
import com.uplants.favorites.service.ServiceResult
import com.uplants.favorites.service.parsePage
import com.uplants.favorites.service.validateListName
import com.uplants.favorites.service.validateListUpdate
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting(service: FavoritesService, auth: AuthVerifier) {
    routing {
        get("/health") { call.respond(mapOf("status" to "ok", "service" to "favorites-service")) }

        route("/api/favorites") {
            get("/health") { call.respond(mapOf("status" to "ok", "service" to "favorites-service")) }

            route("/lists") {
                // My lists (private ones included)
                get {
                    val userId = call.requireUser(auth) ?: return@get
                    call.respondResult(service.listsOf(userId, includePrivate = true))
                }
                post {
                    val userId = call.requireUser(auth) ?: return@post
                    val request = call.receive<NewListRequest>()
                    when (val name = validateListName(request.name)) {
                        is Checked.Invalid -> call.respondError(HttpStatusCode.BadRequest, name.message)
                        is Checked.Valid -> call.respondResult(service.createList(userId, name.value, request.isPublic))
                    }
                }

                route("/{listId}") {
                    // Public lists: anyone. Private lists: only the owner.
                    get {
                        when (val page = parsePage(call.request.queryParameters["limit"], call.request.queryParameters["offset"])) {
                            is Checked.Invalid -> call.respondError(HttpStatusCode.BadRequest, page.message)
                            is Checked.Valid -> call.respondResult(
                                service.listDetail(call.listId, call.optionalUser(auth), page.value.first, page.value.second)
                            )
                        }
                    }
                    patch {
                        val userId = call.requireUser(auth) ?: return@patch
                        when (val changes = validateListUpdate(call.receive<UpdateListRequest>())) {
                            is Checked.Invalid -> call.respondError(HttpStatusCode.BadRequest, changes.message)
                            is Checked.Valid -> call.respondResult(service.updateList(userId, call.listId, changes.value))
                        }
                    }
                    delete {
                        val userId = call.requireUser(auth) ?: return@delete
                        call.respondResult(service.deleteList(userId, call.listId))
                    }

                    put("/posts/{postId}") {
                        val userId = call.requireUser(auth) ?: return@put
                        call.respondResult(service.addPost(userId, call.listId, call.parameters["postId"]!!))
                    }
                    delete("/posts/{postId}") {
                        val userId = call.requireUser(auth) ?: return@delete
                        call.respondResult(service.removePost(userId, call.listId, call.parameters["postId"]!!))
                    }
                }
            }

            // Which of my lists hold this post (for the "save" button)
            get("/posts/{postId}/lists") {
                val userId = call.requireUser(auth) ?: return@get
                call.respondResult(service.membership(userId, call.parameters["postId"]!!))
            }

            // Someone's public lists (all of them if it's you)
            get("/users/{userId}/lists") {
                val ownerId = call.parameters["userId"]!!
                val viewerId = call.optionalUser(auth)
                call.respondResult(service.listsOf(ownerId, includePrivate = viewerId == ownerId))
            }
        }
    }
}

private val ApplicationCall.listId get() = parameters["listId"]!!

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
