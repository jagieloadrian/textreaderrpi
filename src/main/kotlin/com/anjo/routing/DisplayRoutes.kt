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
            val queued = screenDriverService.queueDisplaySwitch(request.type)
            if (!queued) {
                log.warn("Driver switch rejected — queueDisplaySwitch returned false for type: ${request.type}")
                call.respond(
                    HttpStatusCode.BadRequest,
                    DisplaySelectResponse(
                        accepted = false,
                        message = "Driver switch rejected"
                    )
                )
                return@post
            }

            log.info("Driver switch accepted: ${request.type}")
            call.respond(
                DisplaySelectResponse(
                    accepted = true,
                    message = "Driver switch queued: ${request.type}"
                )
            )
        }
    }
}
