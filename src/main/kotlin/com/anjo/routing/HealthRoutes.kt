package com.anjo.routing

import com.anjo.di.installMetricsRateLimiting
import com.anjo.model.HealthDetailResponse
import com.anjo.model.ScreenDriverMetrics
import com.anjo.service.ZoneRegistry
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import java.lang.management.ManagementFactory

fun Route.healthRoutes(
    zoneRegistry: ZoneRegistry,
    screenDriverMetrics: ScreenDriverMetrics,
    metricsRateLimitPerMinute: Int,
) {
    route("/health/detail") {
        installMetricsRateLimiting(metricsRateLimitPerMinute)
        get {
            val runtime = Runtime.getRuntime()
            val zones = zoneRegistry.listAll()
            call.respond(
                HealthDetailResponse(
                    uptime = ManagementFactory.getRuntimeMXBean().uptime,
                    memoryUsed = runtime.totalMemory() - runtime.freeMemory(),
                    memoryMax = runtime.maxMemory(),
                    displayStatus = if (zones.any { it.status == "ONLINE" }) "ONLINE" else "OFFLINE",
                    totalFailures = screenDriverMetrics.failedMeter?.count ?: 0L,
                    zoneErrors = zones.associate { it.id to it.error },
                )
            )
        }
    }
}
