package com.anjo.model

import com.anjo.config.model.MetricsConfig
import com.codahale.metrics.Counter
import com.codahale.metrics.Meter
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.Timer

data class ScreenDriverMetrics(
    val acceptedMeter: Meter? = null,
    val failedMeter: Meter? = null,
    val inFlightCounter: Counter? = null,
    val executionTimer: Timer? = null,
) {
    companion object {
        val DISABLED = ScreenDriverMetrics()

        fun from(registry: MetricRegistry, config: MetricsConfig): ScreenDriverMetrics {
            if (!config.enabled) return DISABLED
            val p = config.prefix
            return ScreenDriverMetrics(
                acceptedMeter = registry.meter("$p.screenDriver.readInput.accepted"),
                failedMeter = registry.meter("$p.screenDriver.readInput.failed"),
                inFlightCounter = registry.counter("$p.screenDriver.readInput.inFlight"),
                executionTimer = registry.timer("$p.screenDriver.readInput.execution"),
            )
        }
    }
}

