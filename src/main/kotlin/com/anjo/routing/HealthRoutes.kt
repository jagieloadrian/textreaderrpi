package com.anjo.routing

import com.anjo.model.HealthDetailResponse
import com.anjo.service.ScreenDriverService
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import java.lang.management.ManagementFactory


fun Route.healthRoutes(screenDriverService: ScreenDriverService) {
    route("/health") {
        get("/detail") {
            val runtime = Runtime.getRuntime()
            val driverStatus = screenDriverService.status()

            call.respond(
                HealthDetailResponse(
                    status = if (driverStatus.hardwareAvailable) "UP" else "DOWN",
                    uptime = ManagementFactory.getRuntimeMXBean().uptime,
                    memoryUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024),
                    memoryMaxMb = runtime.maxMemory() / (1024 * 1024),
                    displayType = screenDriverService.currentDisplayType(),
                    isActive = driverStatus.hardwareAvailable,
                    lastError = driverStatus.error,
                )
            )
        }
    }
}

