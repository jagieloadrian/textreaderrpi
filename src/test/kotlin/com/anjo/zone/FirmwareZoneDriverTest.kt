package com.anjo.zone

import com.anjo.model.Effect
import com.anjo.module
import com.anjo.service.ZoneRegistry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.di.DependencyKey
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.getBlocking
import io.ktor.server.testing.testApplication
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.milliseconds

class FirmwareZoneDriverTest : FunSpec({

    test("send with no session returns false") {
        val driver = FirmwareZoneDriver("pico-salon")
        runTest {
            driver.send("hello", Effect.SCROLL) shouldBe false
        }
    }

    test("status returns OFFLINE and type FIRMWARE when no session attached") {
        val driver = FirmwareZoneDriver("pico-salon")
        val status = driver.status()
        status.status shouldBe "OFFLINE"
        status.type shouldBe "FIRMWARE"
    }

    test("status id matches the id passed to constructor") {
        val driver = FirmwareZoneDriver("pico-salon")
        driver.status().id shouldBe "pico-salon"
    }

    test("stop does not throw when called without a session") {
        val driver = FirmwareZoneDriver("pico-salon")
        driver.stop()
    }

    test("ZoneRegistry registerFirmwareZone adds zone that is found and reports OFFLINE") {
        val registry = ZoneRegistry()
        registry.registerFirmwareZone("pico-salon")
        registry.contains("pico-salon") shouldBe true
        registry.statusOf("pico-salon") shouldBe "OFFLINE"
    }

    test("ZoneRegistry registerFirmwareZone called twice keeps zone registered and OFFLINE") {
        val registry = ZoneRegistry()
        registry.registerFirmwareZone("pico-salon")
        registry.registerFirmwareZone("pico-salon")
        registry.contains("pico-salon") shouldBe true
        registry.statusOf("pico-salon") shouldBe "OFFLINE"
    }

    test("send with attached session and speed param produces JSON frame with speed field") {
        val server = embeddedServer(Netty, port = 0) { module() }
        server.start(wait = false)
        val client = HttpClient(CIO) { install(WebSockets) }
        try {
            val port = server.engine.resolvedConnectors().first().port
            val registry = server.application.dependencies.getBlocking<ZoneRegistry>(DependencyKey<ZoneRegistry>())
            registry.registerFirmwareZone("pico-timing-test")
            val driver = registry.firmwareDriver("pico-timing-test")!!

            var receivedJson: String? = null
            client.webSocket("ws://localhost:$port/ws/zone/pico-timing-test") {
                delay(50.milliseconds)
                driver.send("hi", Effect.SCROLL, speed = 120)
                val frame = incoming.receive() as Frame.Text
                receivedJson = frame.readText()
                close(CloseReason(CloseReason.Codes.NORMAL, "done"))
            }

            receivedJson shouldContain "\"speed\":120"
            receivedJson shouldNotContain "blinkPeriod"
            receivedJson shouldNotContain "fadeSteps"
        } finally {
            client.close()
            server.stop(0, 0)
        }
    }
})
