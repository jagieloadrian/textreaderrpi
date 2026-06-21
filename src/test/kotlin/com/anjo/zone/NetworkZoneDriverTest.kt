package com.anjo.zone

import com.anjo.model.Effect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest

class NetworkZoneDriverTest : FunSpec({

    test("send with null session returns false and status is OFFLINE") {
        val client = HttpClient(MockEngine { respond("", HttpStatusCode.SwitchingProtocols, headersOf()) }) {
            install(WebSockets)
        }
        val driver = NetworkZoneDriver(
            id = "kitchen",
            ip = "192.168.1.50",
            port = 80,
            client = client,
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        )

        runTest {
            val result = driver.send("Hello", Effect.SCROLL)
            result shouldBe false
            driver.status().status shouldBe "OFFLINE"
        }
    }

    test("status returns ZoneStatus with correct id and type") {
        val client = HttpClient(MockEngine { respond("", HttpStatusCode.SwitchingProtocols, headersOf()) }) {
            install(WebSockets)
        }
        val driver = NetworkZoneDriver(
            id = "kitchen",
            ip = "192.168.1.50",
            port = 80,
            client = client,
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        )

        val status = driver.status()

        status.id shouldBe "kitchen"
        status.type shouldBe "MAX7219"
        status.ip shouldBe "192.168.1.50"
        status.status shouldBe "OFFLINE"
    }

    test("stop cancels the scope without throwing") {
        val client = HttpClient(MockEngine { respond("", HttpStatusCode.SwitchingProtocols, headersOf()) }) {
            install(WebSockets)
        }
        val driver = NetworkZoneDriver(
            id = "kitchen",
            ip = "192.168.1.50",
            port = 80,
            client = client,
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        )

        runTest {
            driver.stop()
            driver.send("Hello", Effect.SCROLL) shouldBe false
        }
    }
})
