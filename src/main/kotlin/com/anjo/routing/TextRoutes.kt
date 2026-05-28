package com.anjo.routing

import com.anjo.model.TextRequest
import com.anjo.model.TextResponse
import com.anjo.service.ScreenDriverService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.textRoutes(screenDriverService: ScreenDriverService) {
    post("/text") {
        val request = call.receive<TextRequest>()
        screenDriverService.displayImmediate(request.text, request.effect)
        call.respond(
            HttpStatusCode.Accepted,
            TextResponse(accepted = true, message = "Text queued for rendering")
        )
    }
}
