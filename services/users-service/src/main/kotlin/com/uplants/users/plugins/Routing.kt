package com.uplants.users.plugins

import com.uplants.users.models.ErrorResponse
import com.uplants.users.models.InterestsRequest
import com.uplants.users.models.LoginRequest
import com.uplants.users.models.RefreshRequest
import com.uplants.users.models.RegisterRequest
import com.uplants.users.models.UpdateProfileRequest
import com.uplants.users.service.AuthVerifier
import com.uplants.users.service.Checked
import com.uplants.users.service.ProfilesService
import com.uplants.users.service.ServiceResult
import com.uplants.users.service.SupabaseAuthService
import com.uplants.users.service.parsePage
import com.uplants.users.service.validateInterests
import com.uplants.users.service.validateProfileUpdate
import com.uplants.users.service.validateSearch
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

fun Application.configureRouting(authService: SupabaseAuthService, profiles: ProfilesService, auth: AuthVerifier) {
    routing {
        get("/health") { call.respond(mapOf("status" to "ok", "service" to "users-service")) }

        route("/api/users") {
            get("/health") { call.respond(mapOf("status" to "ok", "service" to "users-service")) }

            // --- Auth ---
            post("/login") { call.respondResult(authService.login(call.receive<LoginRequest>())) }
            post("/register") { call.respondResult(authService.register(call.receive<RegisterRequest>())) }
            post("/refresh") { call.respondResult(authService.refresh(call.receive<RefreshRequest>().refreshToken)) }

            // --- Search (visitors too) ---
            get("/search") {
                val page = parsePage(call.request.queryParameters["limit"], call.request.queryParameters["offset"])
                val q = validateSearch(call.request.queryParameters["q"])
                when {
                    page is Checked.Invalid -> call.respondError(HttpStatusCode.BadRequest, page.message)
                    q is Checked.Invalid -> call.respondError(HttpStatusCode.BadRequest, q.message)
                    page is Checked.Valid && q is Checked.Valid ->
                        call.respondResult(profiles.search(q.value, page.value.first, page.value.second))
                }
            }

            // --- The logged-in user ---
            route("/me") {
                get {
                    val userId = call.requireUser(auth) ?: return@get
                    call.respondResult(profiles.profile(userId, viewerId = null))
                }
                patch {
                    val userId = call.requireUser(auth) ?: return@patch
                    when (val changes = validateProfileUpdate(call.receive<UpdateProfileRequest>())) {
                        is Checked.Invalid -> call.respondError(HttpStatusCode.BadRequest, changes.message)
                        is Checked.Valid -> call.respondResult(profiles.updateProfile(userId, changes.value))
                    }
                }
                get("/interests") {
                    val userId = call.requireUser(auth) ?: return@get
                    call.respondResult(profiles.interests(userId))
                }
                put("/interests") {
                    val userId = call.requireUser(auth) ?: return@put
                    when (val ids = validateInterests(call.receive<InterestsRequest>().categoryIds)) {
                        is Checked.Invalid -> call.respondError(HttpStatusCode.BadRequest, ids.message)
                        is Checked.Valid -> call.respondResult(profiles.setInterests(userId, ids.value))
                    }
                }
            }

            // --- Any user, by id ---
            route("/{id}") {
                get { call.respondResult(profiles.profile(call.userIdParam, call.optionalUser(auth))) }

                put("/follow") {
                    val userId = call.requireUser(auth) ?: return@put
                    call.respondResult(profiles.follow(userId, call.userIdParam))
                }
                delete("/follow") {
                    val userId = call.requireUser(auth) ?: return@delete
                    call.respondResult(profiles.unfollow(userId, call.userIdParam))
                }

                get("/followers") {
                    when (val page = parsePage(call.request.queryParameters["limit"], call.request.queryParameters["offset"])) {
                        is Checked.Invalid -> call.respondError(HttpStatusCode.BadRequest, page.message)
                        is Checked.Valid -> call.respondResult(profiles.followers(call.userIdParam, page.value.first, page.value.second))
                    }
                }
                get("/following") {
                    when (val page = parsePage(call.request.queryParameters["limit"], call.request.queryParameters["offset"])) {
                        is Checked.Invalid -> call.respondError(HttpStatusCode.BadRequest, page.message)
                        is Checked.Valid -> call.respondResult(profiles.following(call.userIdParam, page.value.first, page.value.second))
                    }
                }
            }
        }
    }
}

private val ApplicationCall.userIdParam get() = parameters["id"]!!

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
