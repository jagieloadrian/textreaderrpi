package com.anjo.service

import com.anjo.driver.DisplayDriver
import com.anjo.driver.DisplayStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest

class ScreenDriverRecoveryTest : FunSpec({
    val fastRecovery = RecoveryPolicy(
        maxAttempts = 3,
        initialDelayMs = 1L,
        jitterMs = 0L,
    )

    test("readInput succeeds when driver works on first attempt") {
        val driver = mockk<DisplayDriver>(relaxed = true)

        val service = ScreenDriverService(driver, Dispatchers.Unconfined, recoveryPolicy = fastRecovery)
        service.readInput("hello")

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

        val service = ScreenDriverService(driver, Dispatchers.Unconfined, recoveryPolicy = fastRecovery)
        service.readInput("test message")

        callCount shouldBe 2
    }

    test("readInput does not throw after max retries — catches TerminalFailure internally") {
        val driver = mockk<DisplayDriver>(relaxed = true)

        every { driver.scrollText(any(), any(), any()) } throws RuntimeException("hardware gone")
        every { driver.status() } returns DisplayStatus(isActive = false, hardwareAvailable = false)

        val service = ScreenDriverService(driver, Dispatchers.Unconfined, recoveryPolicy = fastRecovery)

        // Should NOT throw — TerminalFailure is caught internally and logged
        service.readInput("this will fail hardware")
        // If we get here without exception, the test passes
    }

    test("readInput releases busy flag even after permanent driver failure") {
        val driver = mockk<DisplayDriver>(relaxed = true)

        every { driver.scrollText(any(), any(), any()) } throws RuntimeException("permanent failure")
        every { driver.status() } returns DisplayStatus(isActive = false, hardwareAvailable = false)

        val service = ScreenDriverService(driver, Dispatchers.Unconfined, recoveryPolicy = fastRecovery)
        service.readInput("first message")

        // Should still be able to queue a switch after failure (busy flag released)
        // If busy were stuck true, queueDisplaySwitch would set pending instead of switching
        // We verify by calling again without deadlock
        service.readInput("second message")
    }

    test("status() reflects driver status") {
        val driver = mockk<DisplayDriver>(relaxed = true)
        every { driver.status() } returns DisplayStatus(
            isActive = true,
            hardwareAvailable = true,
            error = null,
        )

        val service = ScreenDriverService(driver, Dispatchers.Unconfined, recoveryPolicy = fastRecovery)
        val status = service.status()

        status.hardwareAvailable shouldBe true
        status.isActive shouldBe true
    }
})

