package com.anjo.routing

import com.anjo.model.DisplaySelectRequest
import com.anjo.model.DisplaySelectResponse
import com.anjo.model.DisplayStatusResponse
import com.anjo.service.ScreenDriverService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("DisplayRoutes")

fun Route.displayRoutes(screenDriverService: ScreenDriverService) {
    route("/display") {
        get("/status") {
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

        post("/select") {
            val request = call.receive<DisplaySelectRequest>()
            val normalized = request.type.lowercase()
            val allowed = setOf("max7219", "lcd", "oled")

            if (normalized !in allowed) {
                log.warn("Driver switch rejected — unsupported type: ${request.type}")
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
                log.warn("Driver switch rejected — queueDisplaySwitch returned false for type: $normalized")
                call.respond(
                    HttpStatusCode.BadRequest,
                    DisplaySelectResponse(
                        accepted = false,
                        message = "Driver switch rejected"
                    )
                )
                return@post
            }

            log.info("Driver switch accepted: $normalized")
            call.respond(
                DisplaySelectResponse(
                    accepted = true,
                    message = "Driver switch queued: $normalized"
                )
            )
        }
    }
}
