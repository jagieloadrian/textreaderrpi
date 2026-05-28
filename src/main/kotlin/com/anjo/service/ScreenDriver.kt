package com.anjo.service

import com.anjo.config.model.RetryConfig
import com.anjo.driver.DisplayDriver
import com.anjo.driver.DisplayStatus
import com.anjo.model.ScreenDriverMetrics
import com.codahale.metrics.Timer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.currentCoroutineContext
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

    @Volatile private var currentScheduledId: String? = null
    @Volatile private var currentDisplayJob: Job? = null

    /** Called by ad-hoc POST /api/text — preempts any running scheduled display. */
    suspend fun displayImmediate(text: String, effect: String = "SCROLL") {
        currentDisplayJob?.cancel()
        currentScheduledId = null
        metrics.acceptedMeter?.mark()
        lastSentMessage.set(text)
        val timerContext: Timer.Context? = metrics.executionTimer?.time()
        metrics.inFlightCounter?.inc()
        try {
            displayMutex.withLock {
                executeWithRecovery(text)
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

    /** Called by the scheduler — registers the job for cancellation via displayImmediate. */
    suspend fun displayScheduled(text: String, scheduleId: String, effect: String = "SCROLL") {
        currentScheduledId = scheduleId
        currentDisplayJob = currentCoroutineContext().job
        lastSentMessage.set(text)
        try {
            displayMutex.withLock {
                executeWithRecovery(text)
            }
        } catch (_: CancellationException) {
            // Scheduler handles re-queue; do not rethrow
        } catch (e: Exception) {
            log.error("Scheduled display failed for schedule $scheduleId: ${e.message}", e)
        } finally {
            if (currentScheduledId == scheduleId) {
                currentScheduledId = null
                currentDisplayJob = null
            }
            checkAndPerformPendingSwitch()
        }
    }

    suspend fun readInput(input: String) {
        displayImmediate(input)
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
