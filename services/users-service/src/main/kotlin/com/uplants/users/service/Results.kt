package com.uplants.users.service

import com.uplants.users.models.PostgrestError
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import org.slf4j.LoggerFactory

sealed interface ServiceResult<out T> {
    data class Ok<T>(val value: T, val status: HttpStatusCode = HttpStatusCode.OK) : ServiceResult<T>
    data class Fail(val status: HttpStatusCode, val message: String) : ServiceResult<Nothing>
}

val USER_NOT_FOUND = ServiceResult.Fail(HttpStatusCode.NotFound, "User not found")

private val log = LoggerFactory.getLogger("com.uplants.users.Supabase")

/** Turns a Supabase error into something the app can act on. [missingRow] is the answer for a foreign key error. */
suspend fun SupabaseClient.upstreamError(response: HttpResponse, missingRow: ServiceResult.Fail = USER_NOT_FOUND): ServiceResult.Fail {
    val text = response.bodyAsText()
    val error = runCatching { json.decodeFromString<PostgrestError>(text) }.getOrNull()
    return when (error?.code) {
        "23503" -> missingRow
        "23505" -> ServiceResult.Fail(HttpStatusCode.Conflict, "Already exists")
        "23514", "22P02", "22001" -> ServiceResult.Fail(HttpStatusCode.BadRequest, "Invalid data")
        else -> {
            log.error("Supabase error {}: {}", response.status, text)
            ServiceResult.Fail(HttpStatusCode.BadGateway, "Database error")
        }
    }
}
