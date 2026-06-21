package com.anjo.service

import com.anjo.config.model.MetricsConfig
import com.anjo.model.HardwareMetrics
import com.codahale.metrics.MetricRegistry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class HardwareMetricsTest : FunSpec({

    test("DISABLED sentinel has all null counter fields") {
        HardwareMetrics.DISABLED.displayFailureCounter.shouldBeNull()
        HardwareMetrics.DISABLED.recoveryRetryCounter.shouldBeNull()
        HardwareMetrics.DISABLED.inFlightCounter.shouldBeNull()
        HardwareMetrics.DISABLED.skippedCounter.shouldBeNull()
    }

    test("from() returns DISABLED when config.enabled is false") {
        val registry = MetricRegistry()
        val result = HardwareMetrics.from(registry, MetricsConfig(enabled = false))
        result shouldBe HardwareMetrics.DISABLED
    }

    test("from() returns non-null counters when config.enabled is true") {
        val registry = MetricRegistry()
        val result = HardwareMetrics.from(registry, MetricsConfig(enabled = true))
        result.displayFailureCounter.shouldNotBeNull()
        result.recoveryRetryCounter.shouldNotBeNull()
        result.inFlightCounter.shouldNotBeNull()
        result.skippedCounter.shouldNotBeNull()
    }

    test("from() registers counters under textreaderrpi.hardware prefix keys") {
        val registry = MetricRegistry()
        HardwareMetrics.from(registry, MetricsConfig(enabled = true, prefix = "textreaderrpi"))
        val keys = registry.counters.keys
        keys.contains("textreaderrpi.hardware.display.failures") shouldBe true
        keys.contains("textreaderrpi.hardware.recovery.retries") shouldBe true
        keys.contains("textreaderrpi.hardware.display.inFlight") shouldBe true
        keys.contains("textreaderrpi.hardware.display.skipped") shouldBe true
    }
})
