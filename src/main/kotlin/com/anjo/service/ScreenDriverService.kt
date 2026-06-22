package com.anjo.service

import com.anjo.config.model.RetryConfig
import com.anjo.db.HistoryRepository
import com.anjo.driver.DisplayStatus
import com.anjo.model.BroadcastResult
import com.anjo.model.ConflictPolicy
import com.anjo.model.DisplayEvent
import com.anjo.model.Effect
import com.anjo.model.HardwareMetrics
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

sealed class DisplayResult {
    data class Accepted(val accepted: Boolean) : DisplayResult()
    data class Broadcast(val result: BroadcastResult) : DisplayResult()
    object ZoneOffline : DisplayResult()
    object ZoneNotFound : DisplayResult()
}

class ScreenDriverService(
    private val zoneRegistry: ZoneRegistry,
    private val ioDispatcher: CoroutineDispatcher,
    private val retryConfig: RetryConfig,
    private val metrics: ScreenDriverMetrics,
    private val hardwareMetrics: HardwareMetrics = HardwareMetrics.DISABLED,
    private val historyRepository: HistoryRepository? = null,
    private val displayEventBus: DisplayEventBus? = null,
) {
    private val log = LoggerFactory.getLogger(ScreenDriverService::class.java)

    private val displayScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val mutexes = ConcurrentHashMap<String, Mutex>()
    private val lastSentMessage = AtomicReference<String?>(null)

    @Volatile private var currentScheduledId: String? = null
    @Volatile private var currentDisplayJob: Job? = null

    suspend fun displayImmediate(
        text: String,
        effect: Effect = Effect.SCROLL,
        conflictPolicy: ConflictPolicy = ConflictPolicy.INTERRUPT,
        zoneId: String? = null
    ): DisplayResult {
        if (zoneId != null) {
            if (!zoneRegistry.contains(zoneId)) return DisplayResult.ZoneNotFound
            if (zoneRegistry.statusOf(zoneId) == "OFFLINE") return DisplayResult.ZoneOffline
        }
        val zoneMutex = acquireMutex(zoneId, conflictPolicy) ?: run {
            log.info("SKIP_NEW: display busy for zone=$zoneId, dropping request for text '${text.take(30)}'")
            hardwareMetrics.skippedCounter?.inc()
            return DisplayResult.Accepted(false)
        }
        metrics.acceptedMeter?.mark()
        lastSentMessage.set(text)
        currentDisplayJob?.cancel()
        currentScheduledId = null
        if (zoneId == null) return broadcastImmediate(text, effect, conflictPolicy, zoneMutex)
        currentDisplayJob = displayScope.launch {
            renderImmediate(text, effect, zoneMutex, alreadyLocked = conflictPolicy == ConflictPolicy.SKIP_NEW, zoneId = zoneId)
        }
        return DisplayResult.Accepted(true)
    }

    private suspend fun broadcastImmediate(text: String, effect: Effect, conflictPolicy: ConflictPolicy, zoneMutex: Mutex): DisplayResult {
        val broadcastResult = zoneRegistry.broadcast(text, effect)
        broadcastResult.successful.forEach { id ->
            tryInsertHistory(text, effect.name, "IMMEDIATE", zoneId = id)
        }
        if (conflictPolicy == ConflictPolicy.SKIP_NEW) zoneMutex.unlock()
        return DisplayResult.Broadcast(broadcastResult)
    }

    suspend fun displayScheduled(
        text: String,
        scheduleId: String,
        effect: Effect,
        conflictPolicy: ConflictPolicy = ConflictPolicy.INTERRUPT,
        webhookStatus: String? = null,
        zoneId: String? = null
    ): Boolean {
        val zoneMutex = acquireMutex(zoneId, conflictPolicy) ?: run {
            log.info("SKIP_NEW: display busy, dropping scheduled request id=$scheduleId")
            return false
        }
        currentScheduledId = scheduleId
        currentDisplayJob = currentCoroutineContext().job
        lastSentMessage.set(text)
        return runScheduledRender(text, scheduleId, effect, zoneMutex, alreadyLocked = conflictPolicy == ConflictPolicy.SKIP_NEW, webhookStatus = webhookStatus, zoneId = zoneId)
    }

    fun stop() {
        displayScope.cancel()
    }

    internal suspend fun awaitCurrentJob() {
        currentDisplayJob?.join()
    }

    private fun acquireMutex(zoneId: String?, policy: ConflictPolicy): Mutex? {
        val mutex = mutexes.getOrPut(zoneId ?: "broadcast") { Mutex() }
        if (policy == ConflictPolicy.SKIP_NEW) {
            if (!mutex.tryLock()) return null
        }
        return mutex
    }

    private suspend fun withMutex(mutex: Mutex, alreadyLocked: Boolean, block: suspend () -> Unit) {
        if (alreadyLocked) block() else mutex.withLock { block() }
    }

    private suspend fun renderImmediate(text: String, effect: Effect, mutex: Mutex, alreadyLocked: Boolean, zoneId: String?) {
        val timerContext: Timer.Context? = metrics.executionTimer?.time()
        metrics.inFlightCounter?.inc()
        hardwareMetrics.inFlightCounter?.inc()
        try {
            if (zoneId == null) return
            withMutex(mutex, alreadyLocked) {
                executeWithRecovery(text, effect, zoneId)
                tryInsertHistory(text, effect.name, "IMMEDIATE", zoneId = zoneId)
            }
        } catch (e: Exception) {
            metrics.failedMeter?.mark()
            log.error("Display operation failed after retries: ${e.message}", e)
        } finally {
            metrics.inFlightCounter?.dec()
            hardwareMetrics.inFlightCounter?.dec()
            timerContext?.stop()
            if (alreadyLocked) mutex.unlock()
        }
    }

    private suspend fun runScheduledRender(
        text: String,
        scheduleId: String,
        effect: Effect,
        mutex: Mutex,
        alreadyLocked: Boolean,
        webhookStatus: String? = null,
        zoneId: String? = null,
    ): Boolean {
        var displaySucceeded = false
        try {
            withMutex(mutex, alreadyLocked) {
                executeWithRecovery(text, effect, zoneId)
                displaySucceeded = true
                tryInsertHistory(text, effect.name, "SCHEDULED", scheduleId, webhookStatus, zoneId = zoneId)
            }
        } catch (_: CancellationException) {
        } catch (e: Exception) {
            log.error("Scheduled display failed for schedule $scheduleId: ${e.message}", e)
        } finally {
            if (alreadyLocked) mutex.unlock()
            if (currentScheduledId == scheduleId) {
                currentScheduledId = null
                currentDisplayJob = null
            }
        }
        return displaySucceeded
    }

    private suspend fun tryInsertHistory(
        text: String,
        effect: String,
        source: String,
        scheduleId: String? = null,
        webhookStatus: String? = null,
        zoneId: String? = null,
    ) {
        try {
            val record = historyRepository?.insert(HistoryRecord(text = text, effect = effect, source = source, scheduleId = scheduleId, webhookStatus = webhookStatus, zoneId = zoneId))
            if (record != null) {
                displayEventBus?.emit(DisplayEvent(id = record.id, text = record.text, effect = record.effect, zoneId = record.zoneId, displayedAt = record.displayedAt))
            }
        } catch (e: Exception) {
            log.warn("History insert failed (non-fatal): ${e.message}", e)
        }
    }

    private suspend fun executeWithRecovery(input: String, effect: Effect, zoneId: String?) {
        withContext(ioDispatcher) {
            retryWithBackoff(retryConfig, hardwareMetrics) {
                if (zoneId != null) {
                    zoneRegistry.route(zoneId, input, effect)
                }
            }
        }
    }

    fun status(): DisplayStatus {
        val anyOnline = zoneRegistry.listAll().any { it.status == "ONLINE" }
        val lastMsg = lastSentMessage.get()
        return DisplayStatus(
            isActive = anyOnline,
            hardwareAvailable = anyOnline,
            currentMessage = lastMsg,
            error = if (!anyOnline) "All zones OFFLINE" else null
        )
    }

    fun currentDisplayType(): String {
        val statuses = zoneRegistry.listAll()
        return statuses.firstOrNull()?.type ?: "UNKNOWN"
    }

}
