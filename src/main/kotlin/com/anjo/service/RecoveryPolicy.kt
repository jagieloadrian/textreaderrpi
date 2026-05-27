package com.anjo.service

import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import kotlin.math.min
import kotlin.random.Random

/**
 * Bounded retry policy with exponential backoff and jitter.
 *
 * Typed failure classification:
 * - [RetryableFailure] — transient errors that should be retried (default for unknown exceptions)
 * - [TerminalFailure] — permanent errors that fail fast without retry
 */
class RecoveryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 100L,
    val multiplier: Double = 2.0,
    val jitterMs: Long = 50L,
    val maxDelayMs: Long = 5_000L,
) {
    private val log = LoggerFactory.getLogger(RecoveryPolicy::class.java)

    /** Permanent failure — will not be retried. Throw from the block to fail fast. */
    class TerminalFailure(message: String, cause: Throwable? = null) : Exception(message, cause)

    /** Transient failure — will be retried up to [maxAttempts]. */
    class RetryableFailure(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * Execute [block] with bounded retries.
     *
     * - [TerminalFailure] from [block] propagates immediately (no retry).
     * - [RetryableFailure] or any other exception triggers retry with exponential backoff + jitter.
     * - After [maxAttempts] exhausted, throws [TerminalFailure] wrapping the last exception.
     */
    suspend fun <T> execute(operationName: String = "operation", block: suspend () -> T): T {
        var lastException: Throwable? = null
        var delayMs = initialDelayMs

        for (attempt in 1..maxAttempts) {
            try {
                return block()
            } catch (e: TerminalFailure) {
                log.warn("[$operationName] terminal failure on attempt $attempt: ${e.message}")
                throw e
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxAttempts) {
                val jitter = if (jitterMs > 0) Random.nextLong(jitterMs) else 0L
                    val waitMs = min(delayMs + jitter, maxDelayMs)
                    log.warn(
                        "[$operationName] transient failure on attempt $attempt/$maxAttempts, retrying in ${waitMs}ms: ${e.message}"
                    )
                    delay(waitMs)
                    delayMs = min((delayMs * multiplier).toLong(), maxDelayMs)
                } else {
                    log.error("[$operationName] all $maxAttempts attempts exhausted: ${e.message}")
                }
            }
        }
        throw TerminalFailure("Max retry attempts ($maxAttempts) exceeded for [$operationName]", lastException)
    }
}

