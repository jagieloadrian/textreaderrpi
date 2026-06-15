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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
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

    private val displayScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val displayMutex = Mutex()
    private val pendingDisplayType = AtomicReference<String?>(null)
    private val lastSentMessage = AtomicReference<String?>(null)

    @Volatile private var currentScheduledId: String? = null
    @Volatile private var currentDisplayJob: Job? = null

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
        }
        prepareImmediate(text)
        currentDisplayJob = displayScope.launch {
            renderImmediate(text, effect, alreadyLocked = conflictPolicy == ConflictPolicy.SKIP_NEW)
        }
        return true
    }

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
        }
        currentScheduledId = scheduleId
        currentDisplayJob = currentCoroutineContext().job
        lastSentMessage.set(text)
        return runScheduledRender(text, scheduleId, renderer, effect, alreadyLocked = conflictPolicy == ConflictPolicy.SKIP_NEW)
    }

    fun stop() {
        displayScope.cancel()
    }

    internal suspend fun awaitCurrentJob() {
        currentDisplayJob?.join()
    }

    private fun prepareImmediate(text: String) {
        currentDisplayJob?.cancel()
        currentScheduledId = null
        metrics.acceptedMeter?.mark()
        lastSentMessage.set(text)
    }

    private suspend fun renderImmediate(text: String, effect: Effect, alreadyLocked: Boolean) {
        val timerContext: Timer.Context? = metrics.executionTimer?.time()
        metrics.inFlightCounter?.inc()
        try {
            if (alreadyLocked) {
                executeWithRecovery(text, effectFactory.create(effect))
                tryInsertHistory(text, effect.name, "IMMEDIATE")
            } else {
                displayMutex.withLock {
                    executeWithRecovery(text, effectFactory.create(effect))
                    tryInsertHistory(text, effect.name, "IMMEDIATE")
                }
            }
        } catch (e: Exception) {
            metrics.failedMeter?.mark()
            log.error("Display operation failed after retries: ${e.message}", e)
        } finally {
            metrics.inFlightCounter?.dec()
            timerContext?.stop()
            if (alreadyLocked) displayMutex.unlock()
            checkAndPerformPendingSwitch()
        }
    }

    private suspend fun runScheduledRender(
        text: String,
        scheduleId: String,
        renderer: EffectRenderer,
        effect: Effect,
        alreadyLocked: Boolean,
    ): Boolean {
        var displaySucceeded = false
        try {
            if (alreadyLocked) {
                executeWithRecovery(text, renderer)
                displaySucceeded = true
                tryInsertHistory(text, effect.name, "SCHEDULED", scheduleId)
            } else {
                displayMutex.withLock {
                    executeWithRecovery(text, renderer)
                    displaySucceeded = true
                    tryInsertHistory(text, effect.name, "SCHEDULED", scheduleId)
                }
            }
        } catch (_: CancellationException) {
        } catch (e: Exception) {
            log.error("Scheduled display failed for schedule $scheduleId: ${e.message}", e)
        } finally {
            if (alreadyLocked) displayMutex.unlock()
            if (currentScheduledId == scheduleId) {
                currentScheduledId = null
                currentDisplayJob = null
            }
            checkAndPerformPendingSwitch()
        }
        return displaySucceeded
    }

    private suspend fun tryInsertHistory(
        text: String,
        effect: String,
        source: String,
        scheduleId: String? = null,
    ) {
        try {
            historyRepository?.insert(HistoryRecord(text = text, effect = effect, source = source, scheduleId = scheduleId))
        } catch (e: Exception) {
            log.warn("History insert failed (non-fatal): ${e.message}", e)
        }
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
        if (switched) selectionService.currentDriver()?.let { driver = it }
    }
}
