package com.anjo.model

import com.anjo.config.model.MetricsConfig
import com.codahale.metrics.Counter
import com.codahale.metrics.MetricRegistry

data class HardwareMetrics(
    val displayFailureCounter: Counter? = null,
    val recoveryRetryCounter: Counter? = null,
    val inFlightCounter: Counter? = null,
    val skippedCounter: Counter? = null,
) {
    companion object {
        val DISABLED = HardwareMetrics()

        fun from(registry: MetricRegistry, config: MetricsConfig): HardwareMetrics {
            if (!config.enabled) return DISABLED
            val p = config.prefix
            return HardwareMetrics(
                displayFailureCounter = registry.counter("$p.hardware.display.failures"),
                recoveryRetryCounter = registry.counter("$p.hardware.recovery.retries"),
                inFlightCounter = registry.counter("$p.hardware.display.inFlight"),
                skippedCounter = registry.counter("$p.hardware.display.skipped"),
            )
        }
    }
}
