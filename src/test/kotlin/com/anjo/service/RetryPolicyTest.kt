package com.anjo.service

import com.anjo.config.model.RetryConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe

class RetryPolicyTest : FunSpec({

    test("retryWithBackoff returns result on first success") {
        var callCount = 0
        val result = retryWithBackoff(RetryConfig(maxAttempts = 3, initialDelayMs = 1)) {
            callCount++
            "success"
        }
        result shouldBe "success"
        callCount shouldBe 1
    }

    test("retryWithBackoff retries on transient failure and succeeds") {
        var callCount = 0
        val result = retryWithBackoff(RetryConfig(maxAttempts = 3, initialDelayMs = 1)) {
            callCount++
            if (callCount < 2) throw RuntimeException("transient error")
            "recovered"
        }
        result shouldBe "recovered"
        callCount shouldBe 2
    }

    test("retryWithBackoff rethrows after maxAttempts exhausted") {
        var callCount = 0
        shouldThrow<RuntimeException> {
            retryWithBackoff(RetryConfig(maxAttempts = 3, initialDelayMs = 1)) {
                callCount++
                throw RuntimeException("always fails")
            }
        }
        callCount shouldBe 3
    }

    test("retryWithBackoff makes maxAttempts calls before giving up") {
        val attempts = mutableListOf<Int>()
        shouldThrow<RuntimeException> {
            retryWithBackoff(RetryConfig(maxAttempts = 5, initialDelayMs = 1)) {
                attempts.add(attempts.size + 1)
                throw RuntimeException("persistent failure")
            }
        }
        attempts.size shouldBeGreaterThanOrEqual 5
    }

    test("RetryConfig uses sane defaults") {
        val config = RetryConfig()
        config.maxAttempts shouldBe 5
        config.initialDelayMs shouldBe 1000L
        config.maxDelayMs shouldBe 30000L
        config.factor shouldBe 2.0
    }
})
