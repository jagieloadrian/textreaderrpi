package com.anjo.routing

import com.anjo.config.model.ApiConfig
import com.anjo.db.ScheduleRepository
import com.anjo.di.installApiRateLimiting
import com.anjo.service.MetricsCollector
import com.anjo.service.ReaderInputService
import com.anjo.service.SchedulerService
import com.anjo.service.ScreenDriverService
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
            textRoutes(readerInputService)
            displayRoutes(screenDriverService)
            scheduleRoutes(scheduleRepository, schedulerService)
        }

        swaggerUI(path = "openapi") {
            info = OpenApiInfo(title = "My API", version = "1.0.0")
        }
    }
}
