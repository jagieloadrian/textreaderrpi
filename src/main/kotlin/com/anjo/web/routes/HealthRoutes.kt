package com.anjo.web.routes

import com.anjo.model.ReadinessStatus
import com.anjo.service.HealthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.healthRoutes(healthService: HealthService) {
    get("/health") {
        val status = healthService.liveness()
        call.respond(HttpStatusCode.OK, status)
    }

    get("/health/ready") {
        val readiness = healthService.readiness()
        val httpStatus = when (readiness.status) {
            "UP" -> HttpStatusCode.OK
            else -> HttpStatusCode.ServiceUnavailable
        }
        call.respond(httpStatus, readiness)
    }
}


