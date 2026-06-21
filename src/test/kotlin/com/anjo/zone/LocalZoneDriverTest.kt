package com.anjo.zone

import com.anjo.driver.DisplayDriver
import com.anjo.driver.DisplayStatus
import com.anjo.driver.OfflineDisplayDriver
import com.anjo.model.Effect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest

class LocalZoneDriverTest : FunSpec({
    test("should return true and report ONLINE when healthy driver succeeds") {
        val driver = mockk<DisplayDriver>(relaxed = true)
        every { driver.status() } returns DisplayStatus(isActive = false, hardwareAvailable = true)
        val zoneDriver = LocalZoneDriver(id = "main", type = "MAX7219", driver = driver)

        runTest {
            val result = zoneDriver.send("Hello", Effect.SCROLL)
            result shouldBe true
            zoneDriver.status().status shouldBe "ONLINE"
        }
    }

    test("should return false when the wrapped driver throws on send") {
        val driver = mockk<DisplayDriver>(relaxed = true)
        every { driver.status() } returns DisplayStatus(isActive = false, hardwareAvailable = true)
        every { driver.write(any()) } throws RuntimeException("SPI failure")
        val zoneDriver = LocalZoneDriver(id = "main", type = "MAX7219", driver = driver)

        runTest {
            val result = zoneDriver.send("Hello", Effect.BLINK)
            result shouldBe false
        }
    }

    test("should report OFFLINE when wrapping OfflineDisplayDriver") {
        val zoneDriver = LocalZoneDriver(id = "offline-zone", type = "MAX7219", driver = OfflineDisplayDriver)

        zoneDriver.status().status shouldBe "OFFLINE"
    }
})
