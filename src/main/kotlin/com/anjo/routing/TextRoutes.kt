package com.anjo.routing

import com.anjo.model.TextRequest
import com.anjo.model.TextResponse
import com.anjo.service.ScreenDriverService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("TextRoutes")

fun Route.textRoutes(screenDriverService: ScreenDriverService) {
    post("/text") {
        val request = call.receive<TextRequest>()
        log.info("Text received: length=${request.text.length} effect=${request.effect} conflictPolicy=${request.conflictPolicy}")
        val accepted = screenDriverService.displayImmediate(request.text, request.effect, request.conflictPolicy)
        call.respond(
            HttpStatusCode.Accepted,
            TextResponse(
                accepted = accepted,
                message = if (accepted) "Text queued for rendering" else "Display busy, request skipped"
            )
        )
    }
}
