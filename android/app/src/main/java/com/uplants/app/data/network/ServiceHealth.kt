package com.uplants.app.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** The microservices, by the port they run on (see [ServiceUrls]). */
enum class Service(val port: Int) {
    USERS(8080), CONTENT(8081), INTERACTIONS(8082), FAVORITES(8083), MESSAGING(8084);

    companion object {
        fun ofPort(port: Int): Service? = entries.firstOrNull { it.port == port }
    }
}

/**
 * Requirement 25: which services are failing right now, learnt from real requests (no extra pings).
 * A service is "down" after it fails to answer (or answers 502/503/504) and "up" again on its next good answer.
 * The UI shows a banner naming the parts of the app that are affected; everything else keeps working.
 */
class ServiceHealth {
    private val _down = MutableStateFlow<Set<Service>>(emptySet())
    val down: StateFlow<Set<Service>> = _down.asStateFlow()

    fun report(service: Service, ok: Boolean) = _down.update { if (ok) it - service else it + service }
}

/** Reports every request's outcome to [ServiceHealth]. */
class ServiceHealthInterceptor(private val health: ServiceHealth) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val service = Service.ofPort(chain.request().url.port)
        val response = try {
            chain.proceed(chain.request())
        } catch (e: IOException) {
            // A cancelled call (screen closed) says nothing about the service.
            if (service != null && !chain.call().isCanceled()) health.report(service, ok = false)
            throw e
        }
        if (service != null) health.report(service, ok = response.code !in 502..504)
        return response
    }
}
