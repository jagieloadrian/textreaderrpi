package com.anjo.routing

import com.anjo.service.ScreenDriverService
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.lang.management.ManagementFactory

@Serializable
data class HealthDetailResponse(
    val status: String,
    val uptime: Long,
    val memoryUsedMb: Long,
    val memoryMaxMb: Long,
    val displayType: String,
    val isActive: Boolean,
    val lastError: String? = null,
)

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

