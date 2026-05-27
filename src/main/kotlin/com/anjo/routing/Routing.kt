package com.anjo.routing

import com.anjo.config.model.ApiConfig
import com.anjo.di.installApiRateLimiting
import com.anjo.service.ReaderInputService
import com.anjo.service.ScreenDriverService
import com.anjo.web.routes.webRoutes
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    install(AutoHeadResponse)
    val readerInputService: ReaderInputService by dependencies
    val screenDriverService: ScreenDriverService by dependencies
    val apiConfig: ApiConfig by dependencies

    routing {
        staticResources("/static", "static")
        webRoutes(screenDriverService)
        healthRoutes(screenDriverService)

        route("/api") {
            installApiRateLimiting(apiConfig.rateLimitPerMinute)
            textRoutes(readerInputService)
            displayRoutes(screenDriverService)
        }

        swaggerUI(path = "openapi") {
            info = OpenApiInfo(title = "My API", version = "1.0.0")
        }
    }
}
