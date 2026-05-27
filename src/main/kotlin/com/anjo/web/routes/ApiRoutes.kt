package com.anjo.web.routes

import com.anjo.api.DisplaySelectRequest
import com.anjo.api.DisplaySelectResponse
import com.anjo.api.DisplayStatusResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import com.anjo.service.ScreenDriverService

fun Route.displayApiRoutes(screenDriverService: ScreenDriverService) {
    get("/api/display/status") {
        val status = screenDriverService.status()
        call.respond(
            DisplayStatusResponse(
                displayType = screenDriverService.currentDisplayType(),
                isActive = status.isActive,
                hardwareAvailable = status.hardwareAvailable,
                currentMessage = status.currentMessage,
                error = status.error,
            )
        )
    }

    post("/api/display/select") {
        val request = call.receive<DisplaySelectRequest>()
        val normalized = request.type.lowercase()
        val allowed = setOf("max7219", "lcd", "oled")

        if (normalized !in allowed) {
            call.respond(
                HttpStatusCode.BadRequest,
                DisplaySelectResponse(
                    accepted = false,
                    message = "Unsupported driver type: ${request.type}"
                )
            )
            return@post
        }

        val queued = screenDriverService.queueDisplaySwitch(normalized)
        if (!queued) {
            call.respond(
                HttpStatusCode.BadRequest,
                DisplaySelectResponse(
                    accepted = false,
                    message = "Driver switch rejected"
                )
            )
            return@post
        }

        call.respond(
            DisplaySelectResponse(
                accepted = true,
                message = "Driver switch queued: $normalized"
            )
        )
    }
}

