package com.anjo.service

import com.anjo.driver.DisplayStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.mockk.every
import io.mockk.mockk

class HealthServiceTest : FunSpec({
    val screenDriverService = mockk<ScreenDriverService>()

    beforeEach {
        every { screenDriverService.status() } returns DisplayStatus(
            isActive = true,
            hardwareAvailable = true,
            error = null,
        )
        every { screenDriverService.currentDisplayType() } returns "MAX7219"
    }

    test("liveness() always returns status UP") {
        val sut = HealthService(screenDriverService)
        val result = sut.liveness()

        result.status shouldBe "UP"
    }

    test("liveness() returns non-negative uptime and memory stats") {
        val sut = HealthService(screenDriverService)
        val result = sut.liveness()

        result.uptime shouldBeGreaterThanOrEqualTo 0L
        result.memoryUsedMb shouldBeGreaterThanOrEqualTo 0L
        result.memoryMaxMb shouldBeGreaterThanOrEqualTo 1L
    }

    test("readiness() returns UP when hardware is available") {
        every { screenDriverService.status() } returns DisplayStatus(
            isActive = true,
            hardwareAvailable = true,
            error = null,
        )
        val sut = HealthService(screenDriverService)
        val result = sut.readiness()

        result.status shouldBe "UP"
        result.isActive shouldBe true
        result.lastError shouldBe null
        result.displayType shouldBe "MAX7219"
    }

    test("readiness() returns DOWN when hardware is unavailable") {
        every { screenDriverService.status() } returns DisplayStatus(
            isActive = false,
            hardwareAvailable = false,
            error = "No display driver available",
        )
        val sut = HealthService(screenDriverService)
        val result = sut.readiness()

        result.status shouldBe "DOWN"
        result.isActive shouldBe false
        result.lastError shouldBe "No display driver available"
    }

    test("readiness() reflects displayType from screenDriverService") {
        every { screenDriverService.currentDisplayType() } returns "LCD"
        val sut = HealthService(screenDriverService)
        val result = sut.readiness()

        result.displayType shouldBe "LCD"
    }
})


