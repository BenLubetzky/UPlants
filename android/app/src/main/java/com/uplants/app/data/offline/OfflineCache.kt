package com.uplants.app.data.offline

import com.uplants.app.data.network.ApiErrorKind
import com.uplants.app.data.network.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

@Serializable
private data class CacheEntry<T>(val savedAt: Long, val value: T)

/**
 * Requirement 22: the last answers the app got (feeds, posts, comments, profiles, categories) saved as JSON files,
 * so they can be shown when the device is offline or a service is down. Images are cached separately by Coil.
 * Cleared on log out.
 */
class OfflineCache(private val dir: File) {

    init {
        dir.mkdirs()
    }

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun <T> put(key: String, serializer: KSerializer<T>, value: T) = withContext(Dispatchers.IO) {
        runCatching {
            file(key).writeText(json.encodeToString(CacheEntry.serializer(serializer), CacheEntry(System.currentTimeMillis(), value)))
        }
        Unit
    }

    /** The saved value and when it was saved, or null if there is none (or it can't be read any more). */
    suspend fun <T> get(key: String, serializer: KSerializer<T>): Pair<T, Long>? = withContext(Dispatchers.IO) {
        runCatching {
            val entry = json.decodeFromString(CacheEntry.serializer(serializer), file(key).readText())
            entry.value to entry.savedAt
        }.getOrNull()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.delete() }
        Unit
    }

    /**
     * Runs [fetch]; a good answer is saved under [key]. If the service can't be reached (offline, or the service is
     * down), the saved answer is returned instead, passed through [markCached] with the time it was saved.
     */
    suspend fun <T> withFallback(
        key: String,
        serializer: KSerializer<T>,
        markCached: (T, Long) -> T = { value, _ -> value },
        fetch: suspend () -> Result<T>,
    ): Result<T> {
        val result = fetch()
        result.onSuccess { put(key, serializer, it) }
        val error = result.exceptionOrNull() as? ApiException ?: return result
        if (error.kind != ApiErrorKind.OFFLINE && error.kind != ApiErrorKind.UNAVAILABLE) return result
        val (saved, savedAt) = get(key, serializer) ?: return result
        return Result.success(markCached(saved, savedAt))
    }

    /** File names from keys: hashed, so any characters (filters, ids) are safe. */
    private fun file(key: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.json")
    }
}
