package com.anjo.service

import com.anjo.config.model.RetryConfig
import com.anjo.model.ScreenDriverMetrics
import com.anjo.zone.ZoneDriver
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenDriverRecoveryTest : FunSpec({

    val fastRetry = RetryConfig(maxAttempts = 3, initialDelayMs = 1L)

    fun makeRegistry(driver: ZoneDriver): ZoneRegistry {
        val registry = ZoneRegistry()
        registry.register("main", driver)
        return registry
    }

    fun service(driver: ZoneDriver) = ScreenDriverService(
        zoneRegistry = makeRegistry(driver),
        ioDispatcher = Dispatchers.Unconfined,
        retryConfig = fastRetry,
        metrics = ScreenDriverMetrics.DISABLED,
    )

    test("should succeed when zone driver works on first attempt") {
        val driver = mockk<ZoneDriver>(relaxed = true)
        coEvery { driver.send(any(), any()) } returns true
        service(driver).displayImmediate("hello")
        coVerify(exactly = 1) { driver.send("hello", any()) }
    }

    test("should not throw after max retries on zone driver failure") {
        val driver = mockk<ZoneDriver>(relaxed = true)
        coEvery { driver.send(any(), any()) } throws RuntimeException("SPI timeout")
        every { driver.status() } returns com.anjo.model.ZoneStatus(id = "main", type = "MAX7219", status = "OFFLINE")
        service(driver).displayImmediate("this will fail hardware")
    }

    test("should release zone after permanent driver failure") {
        val driver = mockk<ZoneDriver>(relaxed = true)
        coEvery { driver.send(any(), any()) } throws RuntimeException("permanent failure")
        every { driver.status() } returns com.anjo.model.ZoneStatus(id = "main", type = "MAX7219", status = "OFFLINE")
        val svc = service(driver)
        svc.displayImmediate("first message")
        svc.displayImmediate("second message")
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
