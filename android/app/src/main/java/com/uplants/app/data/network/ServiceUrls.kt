package com.uplants.app.data.network

import com.uplants.app.BuildConfig

/**
 * Each microservice runs on its own port (see the repo README).
 * The host comes from `uplants.apiHost` in gradle.properties.
 */
object ServiceUrls {
    val users = url(8080)
    val content = url(8081)
    val interactions = url(8082)
    val favorites = url(8083)
    val messaging = url(8084)

    private fun url(port: Int) = "${BuildConfig.API_HOST}:$port/"
}
