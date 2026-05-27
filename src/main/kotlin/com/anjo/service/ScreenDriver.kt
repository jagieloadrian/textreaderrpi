package com.anjo.service

import com.anjo.driver.DisplayDriver
import com.anjo.driver.DisplayStatus
import com.codahale.metrics.MetricRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ScreenDriverService(
    private var driver: DisplayDriver,
    private val ioDispatcher: CoroutineDispatcher,
    private val displaySelectionService: DisplaySelectionService? = null,
    private val recoveryPolicy: RecoveryPolicy = RecoveryPolicy(),
    private val metricRegistry: MetricRegistry = MetricRegistry(),
) {
    private val log = LoggerFactory.getLogger(ScreenDriverService::class.java)

    private val busy = AtomicBoolean(false)
    private val pendingDisplayType = AtomicReference<String?>(null)
    private val lastSentMessage = AtomicReference<String?>(null)
    private val acceptedMeter = metricRegistry.meter("screenDriver.readInput.accepted")
    private val failedMeter = metricRegistry.meter("screenDriver.readInput.failed")
    private val inFlightCounter = metricRegistry.counter("screenDriver.readInput.inFlight")
    private val executionTimer = metricRegistry.timer("screenDriver.readInput.execution")

    suspend fun readInput(input: String) {
        require(input.isNotBlank()) { "Text cannot be blank" }
        acceptedMeter.mark()
        lastSentMessage.set(input)
        val timerContext = executionTimer.time()
        busy.set(true)
        inFlightCounter.inc()
        try {
            withContext(ioDispatcher) {
                recoveryPolicy.execute("scrollText") {
                    driver.scrollText(this, input)
                }
            }
        } catch (e: RecoveryPolicy.TerminalFailure) {
            failedMeter.mark()
            log.error("Display operation failed permanently after retries: ${e.message}", e)
        } finally {
            busy.set(false)
            inFlightCounter.dec()
            timerContext.stop()
            checkAndPerformPendingSwitch()
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

        if (busy.get()) {
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
