package com.anjo.service

import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import kotlin.math.min

data class RetryConfig(
    val maxAttempts: Int = 5,
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 30000,
    val factor: Double = 2.0,
)

private val log = LoggerFactory.getLogger("com.anjo.service.RetryPolicy")

suspend fun <T> retryWithBackoff(config: RetryConfig = RetryConfig(), block: suspend () -> T): T {
    var attempt = 0
    var delayMs = config.initialDelayMs
    while (true) {
        try {
            return block()
        } catch (e: Exception) {
            attempt++
            if (attempt >= config.maxAttempts) {
                log.error("All ${config.maxAttempts} retry attempts exhausted: ${e.message}")
                throw e
            }
            log.warn("Attempt $attempt/${config.maxAttempts} failed, retrying in ${delayMs}ms: ${e.message}")
            delay(delayMs)
            delayMs = min((delayMs * config.factor).toLong(), config.maxDelayMs)
        }
    }
}

