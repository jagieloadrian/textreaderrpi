package com.anjo.service

import com.anjo.config.model.MetricsConfig
import com.anjo.config.model.RetryConfig
import com.anjo.driver.DisplayDriver
import com.anjo.driver.DisplayStatus
import com.anjo.model.ScreenDriverMetrics
import com.codahale.metrics.MetricRegistry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers

class ScreenDriverResourceTest : FunSpec({

    val fastRetry = RetryConfig(maxAttempts = 1, initialDelayMs = 1L)
    val metricsConfig = MetricsConfig(enabled = true, prefix = "textreaderrpi")

    fun service(driver: DisplayDriver, registry: MetricRegistry) = ScreenDriverService(
        driver = driver,
        ioDispatcher = Dispatchers.Unconfined,
        retryConfig = fastRetry,
        displaySelectionService = null,
        metrics = ScreenDriverMetrics.from(registry, metricsConfig),
    )

    test("should keep in-flight gauge at zero after completed operations") {
        val driver = mockk<DisplayDriver>(relaxed = true)
        val registry = MetricRegistry()
        val svc = service(driver, registry)
        repeat(5) { svc.readInput("text $it") }
        registry.counter("textreaderrpi.screenDriver.readInput.inFlight").count shouldBe 0L
        registry.meter("textreaderrpi.screenDriver.readInput.accepted").count shouldBe 5L
    }

    test("should count failures and release in-flight counter on error") {
        val driver = mockk<DisplayDriver>(relaxed = true)
        every { driver.scrollText(any(), any(), any()) } throws RuntimeException("hardware error")
        every { driver.status() } returns DisplayStatus(isActive = false, hardwareAvailable = false)
        val registry = MetricRegistry()
        val svc = service(driver, registry)
        svc.readInput("will fail")
        registry.counter("textreaderrpi.screenDriver.readInput.inFlight").count shouldBe 0L
        registry.meter("textreaderrpi.screenDriver.readInput.failed").count shouldBe 1L
    }

    test("should record execution time for readInput") {
        val driver = mockk<DisplayDriver>(relaxed = true)
        val registry = MetricRegistry()
        val svc = service(driver, registry)
        svc.readInput("measure me")
        registry.timer("textreaderrpi.screenDriver.readInput.execution").count shouldBe 1L
    }
})
