package com.anjo.service

import com.anjo.config.model.RetryConfig
import com.anjo.driver.DisplayDriver
import com.anjo.driver.DisplayStatus
import com.anjo.model.ScreenDriverMetrics
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenDriverRecoveryTest : FunSpec({

    val fastRetry = RetryConfig(maxAttempts = 3, initialDelayMs = 1L)

    fun service(driver: DisplayDriver) = ScreenDriverService(
        driver = driver,
        ioDispatcher = Dispatchers.Unconfined,
        retryConfig = fastRetry,
        displaySelectionService = null,
        metrics = ScreenDriverMetrics.DISABLED,
    )

    test("should succeed when driver works on first attempt") {
        val driver = mockk<DisplayDriver>(relaxed = true)
        service(driver).readInput("hello")
        verify(exactly = 1) { driver.scrollText(any(), "hello", any()) }
    }

    test("should retry on transient driver failure and succeed") {
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

    test("should not throw after max retries") {
        val driver = mockk<DisplayDriver>(relaxed = true)
        every { driver.scrollText(any(), any(), any()) } throws RuntimeException("hardware gone")
        every { driver.status() } returns DisplayStatus(isActive = false, hardwareAvailable = false)
        service(driver).readInput("this will fail hardware")
    }

    test("should release mutex after permanent driver failure") {
        val driver = mockk<DisplayDriver>(relaxed = true)
        every { driver.scrollText(any(), any(), any()) } throws RuntimeException("permanent failure")
        every { driver.status() } returns DisplayStatus(isActive = false, hardwareAvailable = false)
        val svc = service(driver)
        svc.readInput("first message")
        svc.readInput("second message")
    }

    test("should reflect driver status") {
        val driver = mockk<DisplayDriver>(relaxed = true)
        every { driver.status() } returns DisplayStatus(isActive = true, hardwareAvailable = true, error = null)
        val status = service(driver).status()
        status.hardwareAvailable shouldBe true
        status.isActive shouldBe true
    }

    test("should succeed on first attempt in retryWithBackoff") {
        runTest {
            val result = retryWithBackoff(RetryConfig(maxAttempts = 3, initialDelayMs = 100L)) { "success" }
            result shouldBe "success"
        }
    }

    test("should retry up to maxAttempts on failure in retryWithBackoff") {
        runTest {
            var attempt = 0
            val result = retryWithBackoff(RetryConfig(maxAttempts = 3, initialDelayMs = 1L)) {
                attempt++
                if (attempt < 3) throw RuntimeException("fail")
                "ok"
            }
            result shouldBe "ok"
            attempt shouldBe 3
        }
    }

    test("should throw after maxAttempts exceeded in retryWithBackoff") {
        runTest {
            shouldThrow<RuntimeException> {
                retryWithBackoff(RetryConfig(maxAttempts = 2, initialDelayMs = 1L)) {
                    throw RuntimeException("always fail")
                }
            }
        }
    }

    test("should make exactly maxAttempts calls with exponential delay in retryWithBackoff") {
        runTest {
            var callCount = 0
            val config = RetryConfig(maxAttempts = 3, initialDelayMs = 100L, factor = 2.0)
            try {
                retryWithBackoff(config) { callCount++; throw RuntimeException("fail") }
            } catch (_: RuntimeException) {}
            callCount shouldBe 3
        }
    }
})
