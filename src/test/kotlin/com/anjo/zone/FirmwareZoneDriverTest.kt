package com.anjo.zone

import com.anjo.model.Effect
import com.anjo.service.ZoneRegistry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest

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
})
