package com.anjo.routing

import com.anjo.config.ReaderInputServiceKey
import com.anjo.service.ReaderInputService
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    install(AutoHeadResponse)

    val readerInputService: ReaderInputService = attributes[ReaderInputServiceKey]

    routing {
        get("/") {
            call.respondText("Hello World!")
        }

        route("/api") {
            textRoutes(readerInputService)
        }

        swaggerUI(path = "openapi") {
            info = OpenApiInfo(title = "My API", version = "1.0.0")
        }
    }
}
