package com.uplants.content

import com.uplants.content.plugins.configureCORS
import com.uplants.content.plugins.configureErrors
import com.uplants.content.plugins.configureRouting
import com.uplants.content.plugins.configureSerialization
import com.uplants.content.service.AuthVerifier
import com.uplants.content.service.ContentService
import com.uplants.content.service.SupabaseClient
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8081
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val supabase = SupabaseClient()
    configureSerialization()
    configureCORS()
    configureErrors()
    configureRouting(ContentService(supabase), AuthVerifier(supabase))
}
