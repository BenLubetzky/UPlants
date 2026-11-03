package com.uplants.content.plugins

import com.uplants.content.models.CreatePostRequest
import com.uplants.content.models.ErrorResponse
import com.uplants.content.models.MediaUpload
import com.uplants.content.service.AuthVerifier
import com.uplants.content.service.Checked
import com.uplants.content.service.ContentService
import com.uplants.content.service.MAX_MEDIA_BYTES
import com.uplants.content.service.ServiceResult
import com.uplants.content.service.parsePostQuery
import com.uplants.content.service.validateNewPost
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json

private val metadataJson = Json { ignoreUnknownKeys = true }

fun Application.configureRouting(content: ContentService, auth: AuthVerifier) {
    routing {
        get("/health") { call.respond(mapOf("status" to "ok", "service" to "content-service")) }

        route("/api/content") {
            get("/health") { call.respond(mapOf("status" to "ok", "service" to "content-service")) }

            get("/categories") { call.respondResult(content.listCategories()) }

            get("/posts") {
                when (val query = parsePostQuery { call.request.queryParameters[it] }) {
                    is Checked.Invalid -> call.respondError(HttpStatusCode.BadRequest, query.message)
                    is Checked.Valid -> call.respondResult(content.listPosts(query.value))
                }
            }

            get("/posts/{id}") { call.respondResult(content.getPost(call.parameters["id"]!!)) }

            post("/posts") {
                val userId = call.requireUser(auth) ?: return@post

                val (metadata, media) = try {
                    call.readPostUpload()
                } catch (e: Exception) {
                    return@post call.respondError(HttpStatusCode.BadRequest, "Send multipart/form-data with a 'metadata' field and an optional 'file' (max 50 MB)")
                }
                val request = try {
                    metadataJson.decodeFromString<CreatePostRequest>(metadata ?: error("missing"))
                } catch (e: Exception) {
                    return@post call.respondError(HttpStatusCode.BadRequest, "'metadata' must be JSON with category_id, content_type and title")
                }

                when (val checked = validateNewPost(request, media)) {
                    is Checked.Invalid -> call.respondError(HttpStatusCode.BadRequest, checked.message)
                    is Checked.Valid -> call.respondResult(content.createPost(userId, checked.value, media))
                }
            }

            delete("/posts/{id}") {
                val userId = call.requireUser(auth) ?: return@delete
                call.respondResult(content.deletePost(userId, call.parameters["id"]!!))
            }
        }
    }
}

/** Reads the `metadata` (JSON text) and `file` parts of a new post. */
private suspend fun ApplicationCall.readPostUpload(): Pair<String?, MediaUpload?> {
    var metadata: String? = null
    var media: MediaUpload? = null
    receiveMultipart(formFieldLimit = MAX_MEDIA_BYTES + 1024 * 1024).forEachPart { part ->
        when {
            part is PartData.FormItem && part.name == "metadata" -> metadata = part.value
            part is PartData.FileItem && part.name == "file" -> media = MediaUpload(
                bytes = part.provider().readRemaining().readByteArray(),
                mimeType = part.contentType?.withoutParameters()?.toString(),
                fileName = part.originalFileName,
            )
        }
        part.dispose()
    }
    return metadata to media
}

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
