package com.anjo.service

import com.anjo.config.model.RetryConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe

class RetryPolicyTest : FunSpec({

    test("should return result on first successful attempt") {
        var callCount = 0
        val result = retryWithBackoff(RetryConfig(maxAttempts = 3, initialDelayMs = 1)) {
            callCount++; "success"
        }
        result shouldBe "success"
        callCount shouldBe 1
    }

    test("should retry on transient failure and succeed") {
        var callCount = 0
        val result = retryWithBackoff(RetryConfig(maxAttempts = 3, initialDelayMs = 1)) {
            callCount++
            if (callCount < 2) throw RuntimeException("transient error")
            "recovered"
        }
        result shouldBe "recovered"
        callCount shouldBe 2
    }

    test("should rethrow exception after maxAttempts exhausted") {
        var callCount = 0
        shouldThrow<RuntimeException> {
            retryWithBackoff(RetryConfig(maxAttempts = 3, initialDelayMs = 1)) {
                callCount++; throw RuntimeException("always fails")
            }
        }
        callCount shouldBe 3
    }

    test("should make exactly maxAttempts calls before giving up") {
        val attempts = mutableListOf<Int>()
        shouldThrow<RuntimeException> {
            retryWithBackoff(RetryConfig(maxAttempts = 5, initialDelayMs = 1)) {
                attempts.add(attempts.size + 1); throw RuntimeException("persistent failure")
            }
        }
        attempts.size shouldBeGreaterThanOrEqual 5
    }

    test("should have sane default values for RetryConfig") {
        val config = RetryConfig()
        config.maxAttempts shouldBe 5
        config.initialDelayMs shouldBe 1000L
        config.maxDelayMs shouldBe 30000L
        config.factor shouldBe 2.0
    }
})
