package com.anjo.routing

import com.anjo.di.installMetricsRateLimiting
import com.anjo.service.MetricsCollector
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.metricsRoutes(metricsCollector: MetricsCollector, metricsRateLimitPerMinute: Int) {
    route("/metrics") {
        installMetricsRateLimiting(metricsRateLimitPerMinute)
        get {
            call.respond(metricsCollector.collect())
        }
    }
}

