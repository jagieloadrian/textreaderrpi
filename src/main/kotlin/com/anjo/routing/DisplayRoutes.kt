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
            call.receive<DisplaySelectRequest>()
            call.respond(
                HttpStatusCode.NotImplemented,
                DisplaySelectResponse(
                    accepted = false,
                    message = "Display type switching is not implemented"
                )
            )
        }
    }
}
