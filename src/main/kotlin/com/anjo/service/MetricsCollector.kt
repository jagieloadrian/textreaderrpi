package com.anjo.service

import com.anjo.model.MetricEntry
import com.anjo.model.MetricGroup
import com.anjo.model.MetricsResponse
import com.codahale.metrics.MetricRegistry
import java.lang.management.ManagementFactory
import java.time.Instant

class MetricsCollector(
    private val metricRegistry: MetricRegistry,
) {
    fun collect(): MetricsResponse = MetricsResponse(
        timestamp = Instant.now().toString(),
        groups = listOf(runtimeGroup(), apiGroup())
    )

    private fun runtimeGroup(): MetricGroup {
        val runtime = Runtime.getRuntime()
        return MetricGroup(
            name = "runtime",
            metrics = listOf(
                MetricEntry(key = "uptime", type = "gauge", value = ManagementFactory.getRuntimeMXBean().uptime.toDouble()),
                MetricEntry(key = "jvm.memory.used", type = "gauge", value = (runtime.totalMemory() - runtime.freeMemory()).toDouble()),
                MetricEntry(key = "jvm.memory.max", type = "gauge", value = runtime.maxMemory().toDouble()),
            )
        )
    }

    private fun apiGroup(): MetricGroup {
        val entries = mutableListOf<MetricEntry>()
        metricRegistry.meters.forEach { (name, meter) ->
            entries.add(MetricEntry(key = name, type = "meter", count = meter.count, meanRate = meter.meanRate))
        }
        metricRegistry.timers.forEach { (name, timer) ->
            entries.add(MetricEntry(key = name, type = "timer", count = timer.count, meanRate = timer.meanRate, p95 = timer.snapshot.get95thPercentile()))
        }
        metricRegistry.counters.forEach { (name, counter) ->
            entries.add(MetricEntry(key = name, type = "counter", count = counter.count))
        }
        return MetricGroup(name = "api", metrics = entries)
    }
}
