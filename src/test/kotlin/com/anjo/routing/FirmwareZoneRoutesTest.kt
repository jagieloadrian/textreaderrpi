package com.anjo.routing

import com.anjo.appTest
import com.anjo.dep
import com.anjo.module
import com.anjo.service.ZoneRegistry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close

class FirmwareZoneRoutesTest : FunSpec({

    test("GET /ws/zone for unknown zone is rejected with CANNOT_ACCEPT close reason") {
        testApplication {
            application { module() }
            val wsClient = createClient { install(WebSockets) }
            var closedWithCannotAccept = false
            wsClient.webSocket("/ws/zone/does-not-exist") {
                val reason = closeReason.await()
                closedWithCannotAccept = reason?.code == CloseReason.Codes.CANNOT_ACCEPT.code
            }
            closedWithCannotAccept shouldBe true
        }
    }

    test("GET /ws/zone for a pre-registered FIRMWARE zone completes handshake") {
        appTest {
            val registry = dep<ZoneRegistry>()
            registry.registerFirmwareZone("pico-ws-test")

            val wsClient = createClient { install(WebSockets) }
            var connected = false
            wsClient.webSocket("/ws/zone/pico-ws-test") {
                connected = true
                close(CloseReason(CloseReason.Codes.NORMAL, "test done"))
            }
            connected shouldBe true
        }
    }

    test("GET /ws/zone for a pre-registered FIRMWARE zone zone returns OFFLINE after disconnect") {
        appTest {
            val registry = dep<ZoneRegistry>()
            registry.registerFirmwareZone("pico-ws-offline")

            val wsClient = createClient { install(WebSockets) }
            wsClient.webSocket("/ws/zone/pico-ws-offline") {
                close(CloseReason(CloseReason.Codes.NORMAL, "test done"))
                closeReason.await()
            }
            registry.statusOf("pico-ws-offline") shouldBe "OFFLINE"
            registry.contains("pico-ws-offline") shouldBe true
        }
    }
})
