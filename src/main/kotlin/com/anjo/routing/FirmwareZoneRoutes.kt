package com.anjo.routing

import com.anjo.service.ZoneRegistry
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.coroutines.channels.ClosedReceiveChannelException

fun Route.firmwareZoneRoutes(zoneRegistry: ZoneRegistry) {
    webSocket("/zone/{id}") {
        val id = call.parameters["id"] ?: return@webSocket
        val driver = zoneRegistry.firmwareDriver(id)
        if (driver == null) {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Zone not found"))
            return@webSocket
        }
        driver.attach(this)
        try {
            for (frame in incoming) {
            }
        } catch (_: ClosedReceiveChannelException) {
        } finally {
            driver.detach()
        }
    }
}
