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
 *
 * Usage: call [execute] with an operation name and a suspend block. The block is retried
 * up to [maxAttempts] times on [RetryableFailure] or any generic exception; [TerminalFailure]
 * propagates immediately without retry.
 */
class RecoveryPolicy(
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    val initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
    val multiplier: Double = DEFAULT_MULTIPLIER,
    val jitterMs: Long = DEFAULT_JITTER_MS,
    val maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
) {
    private val log = LoggerFactory.getLogger(RecoveryPolicy::class.java)

    /** Permanent failure — will not be retried. Throw from the block to fail fast. */
    class TerminalFailure(message: String, cause: Throwable? = null) : Exception(message, cause)

    /** Transient failure — will be retried up to [maxAttempts]. */
    class RetryableFailure(message: String, cause: Throwable? = null) : Exception(message, cause)

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3
        const val DEFAULT_INITIAL_DELAY_MS = 100L
        const val DEFAULT_MULTIPLIER = 2.0
        const val DEFAULT_JITTER_MS = 50L
        const val DEFAULT_MAX_DELAY_MS = 5_000L
    }

    /**
     * Execute [block] with bounded retries.
     *
     * - [TerminalFailure] from [block] propagates immediately (no retry).
     * - [RetryableFailure] or any other exception triggers retry with exponential backoff + jitter.
     * - After [maxAttempts] exhausted, throws [TerminalFailure] wrapping the last exception.
     *
     * @param operationName Human-readable label used in log messages.
     * @param block Suspend block to execute.
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
                    val waitMs = calculateDelay(delayMs)
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

    /** Calculates the capped wait duration for the current delay, adding random jitter. */
    private fun calculateDelay(currentDelay: Long): Long {
        val jitter = if (jitterMs > 0) Random.nextLong(jitterMs) else 0L
        return min(currentDelay + jitter, maxDelayMs)
    }
}
