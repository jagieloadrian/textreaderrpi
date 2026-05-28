package com.anjo.routing

import com.anjo.config.model.ApiConfig
import com.anjo.db.ScheduleRepository
import com.anjo.di.installApiRateLimiting
import com.anjo.routing.ui.scheduleUIRoutes
import com.anjo.routing.ui.webRoutes
import com.anjo.service.MetricsCollector
import com.anjo.service.SchedulerService
import com.anjo.service.ScreenDriverService
import io.ktor.http.ContentType
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.routing.routingRoot

fun Application.configureRouting() {
    install(AutoHeadResponse)
    val screenDriverService: ScreenDriverService by dependencies
    val apiConfig: ApiConfig by dependencies
    val metricsCollector: MetricsCollector by dependencies
    val scheduleRepository: ScheduleRepository by dependencies
    val schedulerService: SchedulerService by dependencies

    routing {
        staticResources("/static", "static")
        webRoutes(screenDriverService)
        scheduleUIRoutes(scheduleRepository)
        metricsRoutes(metricsCollector, apiConfig.metricsRateLimitPerMinute)

        route("/api/v1") {
            installApiRateLimiting(apiConfig.rateLimitPerMinute)
            textRoutes(screenDriverService)
            displayRoutes(screenDriverService)
            scheduleRoutes(scheduleRepository, schedulerService)
        }

        swaggerUI(path = "openapi") {
            info = OpenApiInfo(title = "TextReaderRpi API", version = "1.0.0")
            source = OpenApiDocSource.Routing(ContentType.Application.Json) {
                routingRoot.descendants()
            }
        }
    }
}
