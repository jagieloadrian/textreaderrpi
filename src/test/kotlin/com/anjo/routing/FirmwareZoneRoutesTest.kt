package com.anjo.routing

import com.anjo.module
import com.anjo.service.ZoneRegistry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.di.DependencyKey
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.getBlocking
import io.ktor.client.request.get
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
        testApplication {
            application { module() }
            val client2 = createClient { }
            client2.get("/health")
            val registry = application.dependencies.getBlocking<ZoneRegistry>(DependencyKey<ZoneRegistry>())
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
        testApplication {
            application { module() }
            val client2 = createClient { }
            client2.get("/health")
            val registry = application.dependencies.getBlocking<ZoneRegistry>(DependencyKey<ZoneRegistry>())
            registry.registerFirmwareZone("pico-ws-offline")

            val wsClient = createClient { install(WebSockets) }
            wsClient.webSocket("/ws/zone/pico-ws-offline") {
                close(CloseReason(CloseReason.Codes.NORMAL, "test done"))
            }
            registry.statusOf("pico-ws-offline") shouldBe "OFFLINE"
            registry.contains("pico-ws-offline") shouldBe true
        }
    }
})
