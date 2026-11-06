package com.uplants.messaging

import com.uplants.messaging.plugins.configureCORS
import com.uplants.messaging.plugins.configureErrors
import com.uplants.messaging.plugins.configureRouting
import com.uplants.messaging.plugins.configureSerialization
import com.uplants.messaging.service.AuthVerifier
import com.uplants.messaging.service.MessagingService
import com.uplants.messaging.service.SupabaseClient
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8084
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val supabase = SupabaseClient()
    configureSerialization()
    configureCORS()
    configureErrors()
    configureRouting(MessagingService(supabase), AuthVerifier(supabase))
}
