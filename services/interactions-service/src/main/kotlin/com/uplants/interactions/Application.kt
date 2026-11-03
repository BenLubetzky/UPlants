package com.uplants.interactions

import com.uplants.interactions.plugins.configureCORS
import com.uplants.interactions.plugins.configureErrors
import com.uplants.interactions.plugins.configureRouting
import com.uplants.interactions.plugins.configureSerialization
import com.uplants.interactions.service.AuthVerifier
import com.uplants.interactions.service.InteractionsService
import com.uplants.interactions.service.SupabaseClient
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8082
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val supabase = SupabaseClient()
    configureSerialization()
    configureCORS()
    configureErrors()
    configureRouting(InteractionsService(supabase), AuthVerifier(supabase))
}
