package com.uplants.app.data.network

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/** Why a call to a service failed, in terms the UI can act on. */
enum class ApiErrorKind {
    /** No connection to the service at all (offline, or the container isn't running). */
    OFFLINE,
    /** 401: not logged in, or the session expired. */
    UNAUTHORIZED,
    /** 403: e.g. deleting someone else's post. */
    FORBIDDEN,
    NOT_FOUND,
    /** 409: e.g. a list with that name already exists. */
    CONFLICT,
    /** 400: the service explains what's wrong in [ApiException.serverMessage]. */
    BAD_REQUEST,
    /** 502/503: the service is up but the database isn't reachable. */
    UNAVAILABLE,
    UNKNOWN,
}

class ApiException(val kind: ApiErrorKind, val serverMessage: String? = null, cause: Throwable? = null) :
    Exception(serverMessage ?: kind.name, cause)

@Serializable
private data class ErrorBody(val error: String)

private val errorJson = Json { ignoreUnknownKeys = true }

/**
 * Runs a Retrofit call and turns every failure into an [ApiException], so repositories
 * return `Result<T>` and screens never see raw HTTP or IO exceptions.
 */
suspend fun <T> apiCall(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        val message = e.response()?.errorBody()?.string()?.let {
            runCatching { errorJson.decodeFromString<ErrorBody>(it).error }.getOrNull()
        }
        val kind = when (e.code()) {
            400, 413, 422 -> ApiErrorKind.BAD_REQUEST
            401 -> ApiErrorKind.UNAUTHORIZED
            403 -> ApiErrorKind.FORBIDDEN
            404 -> ApiErrorKind.NOT_FOUND
            409 -> ApiErrorKind.CONFLICT
            502, 503, 504 -> ApiErrorKind.UNAVAILABLE
            else -> ApiErrorKind.UNKNOWN
        }
        Result.failure(ApiException(kind, message, e))
    } catch (e: IOException) {
        Result.failure(ApiException(ApiErrorKind.OFFLINE, cause = e))
    } catch (e: SerializationException) {
        Result.failure(ApiException(ApiErrorKind.UNKNOWN, cause = e))
    }
