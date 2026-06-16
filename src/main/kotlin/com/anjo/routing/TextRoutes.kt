package com.anjo.routing

import com.anjo.model.BroadcastResult
import com.anjo.model.TextRequest
import com.anjo.model.TextResponse
import com.anjo.service.DisplayResult
import com.anjo.service.ScreenDriverService
import com.anjo.service.ZoneRegistry
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("TextRoutes")

private val zoneNameRegex = Regex("^[a-zA-Z0-9-]{1,64}$")

fun Route.textRoutes(screenDriverService: ScreenDriverService, zoneRegistry: ZoneRegistry) {
    post("/text") {
        val request = call.receive<TextRequest>()
        val zoneId = call.request.queryParameters["zone"]

        if (zoneId != null && !zoneNameRegex.matches(zoneId)) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Zone name must be 1-64 alphanumeric characters or dashes"))
            return@post
        }

        log.info("Text received: length=${request.text.length} effect=${request.effect} conflictPolicy=${request.conflictPolicy} zone=$zoneId")

        when (val result = screenDriverService.displayImmediate(request.text, request.effect, request.conflictPolicy, zoneId)) {
            is DisplayResult.ZoneNotFound -> call.respond(HttpStatusCode.NotFound, mapOf("error" to "Zone '$zoneId' not found"))
            is DisplayResult.ZoneOffline -> call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Zone '$zoneId' is OFFLINE"))
            is DisplayResult.Accepted -> call.respond(
                HttpStatusCode.Accepted,
                TextResponse(
                    accepted = result.accepted,
                    message = if (result.accepted) "Text queued for rendering" else "Display busy, request skipped"
                )
            )
            is DisplayResult.Broadcast -> call.respond(HttpStatusCode.Accepted, result.result)
        }
    }
}
