package com.anjo.service
import com.anjo.driver.DisplayDriver
import com.anjo.driver.DisplayStatus
import com.codahale.metrics.MetricRegistry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
class ScreenDriverResourceTest : FunSpec({
    val fastRetry = RetryConfig(maxAttempts = 1, initialDelayMs = 1L)
    test("repeated readInput operations keep in-flight gauge at zero after completion") {
        val driver = mockk<DisplayDriver>(relaxed = true)
        val metrics = MetricRegistry()
        val service = ScreenDriverService(
            driver, Dispatchers.Unconfined,
            retryConfig = fastRetry,
            metricRegistry = metrics,
        )
        repeat(5) { service.readInput("text $it") }
        metrics.counter("screenDriver.readInput.inFlight").count shouldBe 0L
        metrics.meter("screenDriver.readInput.accepted").count shouldBe 5L
    }

    test("failed display operations are counted and in-flight counter is released") {
        val driver = mockk<DisplayDriver>(relaxed = true)
        every { driver.scrollText(any(), any(), any()) } throws RuntimeException("hardware error")
        every { driver.status() } returns DisplayStatus(isActive = false, hardwareAvailable = false)
        val metrics = MetricRegistry()
        val service = ScreenDriverService(
            driver, Dispatchers.Unconfined,
            retryConfig = fastRetry,
            metricRegistry = metrics,
        )
        service.readInput("will fail")
        metrics.counter("screenDriver.readInput.inFlight").count shouldBe 0L
        metrics.meter("screenDriver.readInput.failed").count shouldBe 1L
    }

    test("readInput execution time is recorded") {
        val driver = mockk<DisplayDriver>(relaxed = true)
        val metrics = MetricRegistry()
        val service = ScreenDriverService(
            driver, Dispatchers.Unconfined,
            retryConfig = fastRetry,
            metricRegistry = metrics,
        )
        service.readInput("measure me")
        metrics.timer("screenDriver.readInput.execution").count shouldBe 1L
    }
})
