package com.anjo.driver

import com.anjo.config.model.RetryConfig
import com.anjo.model.ScreenDriverMetrics
import com.anjo.model.ZoneStatus
import com.anjo.service.ScreenDriverService
import com.anjo.service.ZoneRegistry
import com.anjo.zone.ZoneDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers

class DriverIntegrationTest : FunSpec({

    test("currentDisplayType returns type of first registered zone") {
        val driver = mockk<ZoneDriver>(relaxed = true)
        every { driver.status() } returns ZoneStatus(id = "main", type = "MAX7219", status = "ONLINE")
        val registry = ZoneRegistry()
        registry.register("main", driver)
        val service = ScreenDriverService(
            zoneRegistry = registry,
            ioDispatcher = Dispatchers.Unconfined,
            retryConfig = RetryConfig(maxAttempts = 1, initialDelayMs = 1L),
            metrics = ScreenDriverMetrics.DISABLED,
        )
        service.currentDisplayType() shouldBe "MAX7219"
    }
})
