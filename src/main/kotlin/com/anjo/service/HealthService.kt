package com.anjo.service

import com.anjo.model.HealthStatus
import com.anjo.model.ReadinessStatus
import org.slf4j.LoggerFactory

class HealthService(
    private val screenDriverService: ScreenDriverService,
    private val startTime: Long = System.currentTimeMillis(),
) {
    private val log = LoggerFactory.getLogger(HealthService::class.java)

    init {
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576
        val maxMb = runtime.maxMemory() / 1_048_576
        log.info("HealthService started, heap: {}mb/{}mb", usedMb, maxMb)
    }

    fun liveness(): HealthStatus {
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576
        val maxMb = runtime.maxMemory() / 1_048_576
        val uptimeSeconds = (System.currentTimeMillis() - startTime) / 1_000
        return HealthStatus(
            status = "UP",
            uptime = uptimeSeconds,
            memoryUsedMb = usedMb,
            memoryMaxMb = maxMb,
        )
    }

    fun readiness(): ReadinessStatus {
        val driverStatus = screenDriverService.status()
        val displayType = screenDriverService.currentDisplayType()
        val ready = driverStatus.hardwareAvailable
        return ReadinessStatus(
            status = if (ready) "UP" else "DOWN",
            displayType = displayType,
            isActive = driverStatus.isActive,
            lastError = driverStatus.error,
        )
    }
}


