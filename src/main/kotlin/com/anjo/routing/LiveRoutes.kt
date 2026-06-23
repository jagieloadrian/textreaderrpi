package com.anjo.routing

import com.anjo.service.DisplayEventBus
import io.ktor.server.routing.Route
import io.ktor.server.sse.heartbeat
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

fun Route.liveRoutes(displayEventBus: DisplayEventBus) {
    sse("/live") {
        heartbeat {
            period = 30.seconds
            eventProvider = { ServerSentEvent(comments = "keep-alive") }
        }
        displayEventBus.events.collect { event ->
            send(ServerSentEvent(event = "display", data = Json.encodeToString(event)))
        }
    }
}
