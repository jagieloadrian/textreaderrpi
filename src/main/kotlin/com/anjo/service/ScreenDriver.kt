package com.anjo.service

import com.anjo.driver.DisplayStatus
import com.anjo.driver.DisplayDriver
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
) {
    private val log = LoggerFactory.getLogger(ScreenDriverService::class.java)
    private val busy = AtomicBoolean(false)
    private val pendingDisplayType = AtomicReference<String?>(null)
    private val lastSentMessage = AtomicReference<String?>(null)

    suspend fun readInput(input: String) {
        require(input.isNotBlank()) { "Text cannot be blank" }
        lastSentMessage.set(input)
        busy.set(true)
        try {
            withContext(ioDispatcher) {
                recoveryPolicy.execute("scrollText") {
                    driver.scrollText(this, input)
                }
            }
        } catch (e: RecoveryPolicy.TerminalFailure) {
            log.error("Display operation failed permanently after retries: ${e.message}", e)
        } finally {
            busy.set(false)
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
