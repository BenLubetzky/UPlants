package com.uplants.favorites

import com.uplants.favorites.plugins.configureCORS
import com.uplants.favorites.plugins.configureErrors
import com.uplants.favorites.plugins.configureRouting
import com.uplants.favorites.plugins.configureSerialization
import com.uplants.favorites.service.AuthVerifier
import com.uplants.favorites.service.FavoritesService
import com.uplants.favorites.service.SupabaseClient
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8083
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val supabase = SupabaseClient()
    configureSerialization()
    configureCORS()
    configureErrors()
    configureRouting(FavoritesService(supabase), AuthVerifier(supabase))
}
