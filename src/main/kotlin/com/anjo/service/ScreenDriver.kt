package com.anjo.service

import com.anjo.config.model.RetryConfig
import com.anjo.driver.DisplayDriver
import com.anjo.driver.DisplayStatus
import com.anjo.model.ScreenDriverMetrics
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
    private val retryConfig: RetryConfig,
    private val displaySelectionService: DisplaySelectionService?,
    private val metrics: ScreenDriverMetrics,
) {
    private val log = LoggerFactory.getLogger(ScreenDriverService::class.java)

    private val displayMutex = Mutex()
    private val pendingDisplayType = AtomicReference<String?>(null)
    private val lastSentMessage = AtomicReference<String?>(null)

    suspend fun readInput(input: String) {
        require(input.isNotBlank()) { "Text cannot be blank" }
        metrics.acceptedMeter?.mark()
        lastSentMessage.set(input)
        val timerContext: Timer.Context? = metrics.executionTimer?.time()
        metrics.inFlightCounter?.inc()
        try {
            displayMutex.withLock {
                executeWithRecovery(input)
            }
        } catch (e: Exception) {
            metrics.failedMeter?.mark()
            log.error("Display operation failed after retries: ${e.message}", e)
        } finally {
            metrics.inFlightCounter?.dec()
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
