package com.anjo.routing

import com.anjo.model.TextRequest
import com.anjo.model.TextResponse
import com.anjo.service.ReaderInputService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.textRoutes(readerInputService: ReaderInputService) {
    post("/api/text") {
        val request = call.receive<TextRequest>()
        readerInputService.readInput(request.text)

        call.respond(
            HttpStatusCode.Accepted,
            TextResponse(accepted = true, message = "Text queued for rendering")
        )
    }
}

