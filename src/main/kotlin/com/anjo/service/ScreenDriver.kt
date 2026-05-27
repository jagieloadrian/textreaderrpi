package com.anjo.service

import com.anjo.config.model.MetricsConfig
import com.anjo.driver.DisplayDriver
import com.anjo.driver.DisplayStatus
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.Timer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

class ScreenDriverService(
    private var driver: DisplayDriver,
    private val ioDispatcher: CoroutineDispatcher,
    private val displaySelectionService: DisplaySelectionService? = null,
    private val retryConfig: RetryConfig = RetryConfig(),
    private val metricRegistry: MetricRegistry = MetricRegistry(),
    private val metricsConfig: MetricsConfig = MetricsConfig(),
) {
    private val log = LoggerFactory.getLogger(ScreenDriverService::class.java)

    private val displayMutex = Mutex()
    private val pendingDisplayType = AtomicReference<String?>(null)
    private val lastSentMessage = AtomicReference<String?>(null)
    private val p = metricsConfig.prefix
    private val acceptedMeter = if (metricsConfig.enabled) metricRegistry.meter("$p.screenDriver.readInput.accepted") else null
    private val failedMeter = if (metricsConfig.enabled) metricRegistry.meter("$p.screenDriver.readInput.failed") else null
    private val inFlightCounter = if (metricsConfig.enabled) metricRegistry.counter("$p.screenDriver.readInput.inFlight") else null
    private val executionTimer = if (metricsConfig.enabled) metricRegistry.timer("$p.screenDriver.readInput.execution") else null

    suspend fun readInput(input: String) {
        require(input.isNotBlank()) { "Text cannot be blank" }
        acceptedMeter?.mark()
        lastSentMessage.set(input)
        val timerContext: Timer.Context? = executionTimer?.time()
        inFlightCounter?.inc()
        try {
            displayMutex.withLock {
                executeWithRecovery(input)
            }
        } catch (e: Exception) {
            failedMeter?.mark()
            log.error("Display operation failed after retries: ${e.message}", e)
        } finally {
            inFlightCounter?.dec()
            timerContext?.stop()
            checkAndPerformPendingSwitch()
        }
    }

    private suspend fun executeWithRecovery(input: String) {
        withContext(ioDispatcher) {
            retryWithBackoff(retryConfig) {
                driver.scrollText(this, input)
            }
        }
    }

    fun status(): DisplayStatus {
        val driverStatus = driver.status()
        return driverStatus.copy(
            currentMessage = driverStatus.currentMessage ?: lastSentMessage.get()
        )
    }

    fun currentDisplayType(): String = displaySelectionService?.getCurrentDisplayType() ?: "MAX7219"

    fun queueDisplaySwitch(displayType: String): Boolean {
        val normalizedType = displayType.uppercase()
        val selectionService = displaySelectionService ?: return false

        if (displayMutex.isLocked) {
            pendingDisplayType.set(normalizedType)
            return true
        }

        val switched = selectionService.selectDisplay(normalizedType)
        if (switched) {
            selectionService.currentDriver()?.let { newDriver ->
                driver = newDriver
            }
        }
        return switched
    }

    private fun checkAndPerformPendingSwitch() {
        val nextType = pendingDisplayType.getAndSet(null) ?: return
        val selectionService = displaySelectionService ?: return
        val switched = selectionService.selectDisplay(nextType)
        if (switched) {
            selectionService.currentDriver()?.let { newDriver ->
                driver = newDriver
            }
        }
    }
}
