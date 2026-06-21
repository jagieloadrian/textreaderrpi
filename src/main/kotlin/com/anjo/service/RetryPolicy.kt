package com.anjo.service

import com.anjo.config.model.RetryConfig
import com.anjo.model.HardwareMetrics
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

private val log = LoggerFactory.getLogger("com.anjo.service.RetryPolicy")

suspend fun <T> retryWithBackoff(
    config: RetryConfig = RetryConfig(),
    hardwareMetrics: HardwareMetrics? = null,
    block: suspend () -> T,
): T {
    var attempt = 0
    var delayMs = config.initialDelayMs
    while (true) {
        try {
            return block()
        } catch (e: Exception) {
            attempt++
            if (attempt >= config.maxAttempts) {
                log.error("All ${config.maxAttempts} retry attempts exhausted: ${e.message}")
                hardwareMetrics?.displayFailureCounter?.inc()
                throw e
            }
            log.warn("Attempt $attempt/${config.maxAttempts} failed, retrying in ${delayMs}ms: ${e.message}")
            hardwareMetrics?.recoveryRetryCounter?.inc()
            delay(delayMs.milliseconds)
            delayMs = min((delayMs * config.factor).toLong(), config.maxDelayMs)
        }
    }
}

