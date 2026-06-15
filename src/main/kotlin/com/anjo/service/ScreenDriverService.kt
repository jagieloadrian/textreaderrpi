package com.anjo.service

import com.anjo.config.model.RetryConfig
import com.anjo.db.HistoryRepository
import com.anjo.driver.DisplayDriver
import com.anjo.driver.DisplayStatus
import com.anjo.service.effect.EffectRenderer
import com.anjo.model.ConflictPolicy
import com.anjo.model.Effect
import com.anjo.model.HistoryRecord
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
    private val effectFactory: EffectRendererFactory = EffectRendererFactory(),
    private val historyRepository: HistoryRepository? = null,
) {
    private val log = LoggerFactory.getLogger(ScreenDriverService::class.java)

    private val displayMutex = Mutex()
    private val pendingDisplayType = AtomicReference<String?>(null)
    private val lastSentMessage = AtomicReference<String?>(null)

    @Volatile private var currentScheduledId: String? = null
    @Volatile private var currentDisplayJob: Job? = null

    /** Called by ad-hoc POST /api/text — preempts any running scheduled display. */
    suspend fun displayImmediate(
        text: String,
        effect: Effect = Effect.SCROLL,
        conflictPolicy: ConflictPolicy = ConflictPolicy.INTERRUPT
    ): Boolean {
        if (conflictPolicy == ConflictPolicy.SKIP_NEW) {
            if (!displayMutex.tryLock()) {
                log.info("SKIP_NEW: display busy, dropping ad-hoc request for text '${text.take(30)}'")
                return false
            }
            try {
                currentDisplayJob?.cancel()
                currentScheduledId = null
                metrics.acceptedMeter?.mark()
                lastSentMessage.set(text)
                val timerContext: Timer.Context? = metrics.executionTimer?.time()
                metrics.inFlightCounter?.inc()
                try {
                    executeWithRecovery(text, effectFactory.create(effect))
                    try { historyRepository?.insert(HistoryRecord(text = text, effect = effect.name, source = "IMMEDIATE")) } catch (e: Exception) { log.warn("History insert failed (non-fatal): ${e.message}", e) }
                } catch (e: Exception) {
                    metrics.failedMeter?.mark()
                    log.error("Display operation failed after retries: ${e.message}", e)
                } finally {
                    metrics.inFlightCounter?.dec()
                    timerContext?.stop()
                }
            } finally {
                displayMutex.unlock()
                checkAndPerformPendingSwitch()
            }
            return true
        }
        currentDisplayJob?.cancel()
        currentScheduledId = null
        metrics.acceptedMeter?.mark()
        lastSentMessage.set(text)
        val timerContext: Timer.Context? = metrics.executionTimer?.time()
        metrics.inFlightCounter?.inc()
        try {
            displayMutex.withLock {
                executeWithRecovery(text, effectFactory.create(effect))
                try { historyRepository?.insert(HistoryRecord(text = text, effect = effect.name, source = "IMMEDIATE")) } catch (e: Exception) { log.warn("History insert failed (non-fatal): ${e.message}", e) }
            }
        } catch (e: Exception) {
            metrics.failedMeter?.mark()
            log.error("Display operation failed after retries: ${e.message}", e)
        } finally {
            metrics.inFlightCounter?.dec()
            timerContext?.stop()
            checkAndPerformPendingSwitch()
        }
        return true
    }

    /** Called by the scheduler — registers the job for cancellation via displayImmediate. */
    suspend fun displayScheduled(
        text: String,
        scheduleId: String,
        renderer: EffectRenderer,
        effect: Effect,
        conflictPolicy: ConflictPolicy = ConflictPolicy.INTERRUPT
    ): Boolean {
        if (conflictPolicy == ConflictPolicy.SKIP_NEW) {
            if (!displayMutex.tryLock()) {
                log.info("SKIP_NEW: display busy, dropping scheduled request id=$scheduleId")
                return false
            }
            currentScheduledId = scheduleId
            currentDisplayJob = currentCoroutineContext().job
            lastSentMessage.set(text)
            var displaySucceeded = false
            try {
                executeWithRecovery(text, renderer)
                displaySucceeded = true
                try { historyRepository?.insert(HistoryRecord(text = text, effect = effect.name, source = "SCHEDULED", scheduleId = scheduleId)) } catch (e: Exception) { log.warn("History insert failed (non-fatal): ${e.message}", e) }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                log.error("Scheduled display failed for schedule $scheduleId: ${e.message}", e)
            } finally {
                displayMutex.unlock()
                if (currentScheduledId == scheduleId) {
                    currentScheduledId = null
                    currentDisplayJob = null
                }
                checkAndPerformPendingSwitch()
            }
            return displaySucceeded
        }
        currentScheduledId = scheduleId
        currentDisplayJob = currentCoroutineContext().job
        lastSentMessage.set(text)
        var displaySucceeded = false
        try {
            displayMutex.withLock {
                executeWithRecovery(text, renderer)
                displaySucceeded = true
                try { historyRepository?.insert(HistoryRecord(text = text, effect = effect.name, source = "SCHEDULED", scheduleId = scheduleId)) } catch (e: Exception) { log.warn("History insert failed (non-fatal): ${e.message}", e) }
            }
        } catch (_: CancellationException) {
        } catch (e: Exception) {
            log.error("Scheduled display failed for schedule $scheduleId: ${e.message}", e)
        } finally {
            if (currentScheduledId == scheduleId) {
                currentScheduledId = null
                currentDisplayJob = null
            }
            checkAndPerformPendingSwitch()
        }
        return displaySucceeded
    }

    private suspend fun executeWithRecovery(input: String, renderer: EffectRenderer) {
        withContext(ioDispatcher) {
            retryWithBackoff(retryConfig) {
                renderer.render(input, driver)
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

        if (displayMutex.tryLock()) {
            try {
                val switched = selectionService.selectDisplay(normalizedType)
                if (switched) selectionService.currentDriver()?.let { driver = it }
                return switched
            } finally {
                displayMutex.unlock()
            }
        }
        pendingDisplayType.set(normalizedType)
        return true
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
