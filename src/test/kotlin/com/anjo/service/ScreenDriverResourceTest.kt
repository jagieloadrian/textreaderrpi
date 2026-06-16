package com.anjo.service

import com.anjo.config.model.MetricsConfig
import com.anjo.config.model.RetryConfig
import com.anjo.model.ScreenDriverMetrics
import com.anjo.model.ZoneStatus
import com.anjo.zone.ZoneDriver
import com.codahale.metrics.MetricRegistry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers

class ScreenDriverResourceTest : FunSpec({

    val fastRetry = RetryConfig(maxAttempts = 1, initialDelayMs = 1L)
    val metricsConfig = MetricsConfig(enabled = true, prefix = "textreaderrpi")

    fun makeRegistry(driver: ZoneDriver): ZoneRegistry {
        val registry = ZoneRegistry()
        registry.register("main", driver)
        return registry
    }

    fun service(driver: ZoneDriver, registry: MetricRegistry) = ScreenDriverService(
        zoneRegistry = makeRegistry(driver),
        ioDispatcher = Dispatchers.Unconfined,
        retryConfig = fastRetry,
        metrics = ScreenDriverMetrics.from(registry, metricsConfig),
    )

    test("should keep in-flight gauge at zero after completed operations") {
        val driver = mockk<ZoneDriver>(relaxed = true)
        every { driver.status() } returns ZoneStatus(id = "main", type = "MAX7219", status = "ONLINE")
        val registry = MetricRegistry()
        val svc = service(driver, registry)
        repeat(5) { svc.displayImmediate("text $it", zoneId = "main") }
        registry.counter("textreaderrpi.screenDriver.readInput.inFlight").count shouldBe 0L
        registry.meter("textreaderrpi.screenDriver.readInput.accepted").count shouldBe 5L
    }

    test("should count failures and release in-flight counter on error") {
        val driver = mockk<ZoneDriver>(relaxed = true)
        every { driver.send(any(), any()) } throws RuntimeException("hardware error")
        every { driver.status() } returns ZoneStatus(id = "main", type = "MAX7219", status = "OFFLINE")
        val registry = MetricRegistry()
        val svc = service(driver, registry)
        svc.displayImmediate("will fail")
        registry.counter("textreaderrpi.screenDriver.readInput.inFlight").count shouldBe 0L
    }

    test("should record execution time for readInput") {
        val driver = mockk<ZoneDriver>(relaxed = true)
        every { driver.status() } returns ZoneStatus(id = "main", type = "MAX7219", status = "ONLINE")
        val registry = MetricRegistry()
        val svc = service(driver, registry)
        svc.displayImmediate("measure me", zoneId = "main")
        registry.timer("textreaderrpi.screenDriver.readInput.execution").count shouldBe 1L
    }
})
