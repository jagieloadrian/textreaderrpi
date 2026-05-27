package com.anjo.service

import com.anjo.config.model.RetryConfig
import com.anjo.driver.DisplayDriver
import com.anjo.driver.DisplayStatus
import com.anjo.model.ScreenDriverMetrics
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers

class ScreenDriverRecoveryTest : FunSpec({
    val fastRetry = RetryConfig(maxAttempts = 3, initialDelayMs = 1L)

    fun service(driver: DisplayDriver) = ScreenDriverService(
        driver = driver,
        ioDispatcher = Dispatchers.Unconfined,
        retryConfig = fastRetry,
        displaySelectionService = null,
        metrics = ScreenDriverMetrics.DISABLED,
    )

    test("readInput succeeds when driver works on first attempt") {
        val driver = mockk<DisplayDriver>(relaxed = true)
        service(driver).readInput("hello")
        verify(exactly = 1) { driver.scrollText(any(), "hello", any()) }
    }

    test("readInput retries on transient driver failure and succeeds") {
        val driver = mockk<DisplayDriver>(relaxed = true)
        var callCount = 0
        every { driver.scrollText(any(), any(), any()) } answers {
            callCount++
            if (callCount < 2) throw RuntimeException("SPI timeout")
        }
        every { driver.status() } returns DisplayStatus(isActive = true, hardwareAvailable = true)

        service(driver).readInput("test message")

        callCount shouldBe 2
    }

    test("readInput does not throw after max retries — catches exception internally") {
        val driver = mockk<DisplayDriver>(relaxed = true)
        every { driver.scrollText(any(), any(), any()) } throws RuntimeException("hardware gone")
        every { driver.status() } returns DisplayStatus(isActive = false, hardwareAvailable = false)

        service(driver).readInput("this will fail hardware")
    }

    test("readInput releases Mutex even after permanent driver failure") {
        val driver = mockk<DisplayDriver>(relaxed = true)
        every { driver.scrollText(any(), any(), any()) } throws RuntimeException("permanent failure")
        every { driver.status() } returns DisplayStatus(isActive = false, hardwareAvailable = false)

        val svc = service(driver)
        svc.readInput("first message")
        svc.readInput("second message")
    }

    test("status() reflects driver status") {
        val driver = mockk<DisplayDriver>(relaxed = true)
        every { driver.status() } returns DisplayStatus(
            isActive = true,
            hardwareAvailable = true,
            error = null,
        )

        val status = service(driver).status()

        status.hardwareAvailable shouldBe true
        status.isActive shouldBe true
    }
})
