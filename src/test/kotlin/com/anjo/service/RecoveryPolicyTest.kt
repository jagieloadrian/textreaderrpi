package com.anjo.service

import com.anjo.service.RecoveryPolicy.RetryableFailure
import com.anjo.service.RecoveryPolicy.TerminalFailure
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.util.concurrent.atomic.AtomicInteger

class RecoveryPolicyTest : FunSpec({
    val fastPolicy = RecoveryPolicy(
        maxAttempts = 3,
        initialDelayMs = 1L,
        jitterMs = 0L,
    )

    test("succeeds on first attempt without retry") {
        val callCount = AtomicInteger(0)

        val result = fastPolicy.execute("test") {
            callCount.incrementAndGet()
            "ok"
        }

        result shouldBe "ok"
        callCount.get() shouldBe 1
    }

    test("retries transient failures and succeeds after one failure") {
        val callCount = AtomicInteger(0)

        val result = fastPolicy.execute("test") {
            if (callCount.incrementAndGet() < 2) {
                throw RetryableFailure("transient error")
            }
            "recovered"
        }

        result shouldBe "recovered"
        callCount.get() shouldBe 2
    }

    test("throws TerminalFailure after max attempts exhausted on RetryableFailure") {
        val callCount = AtomicInteger(0)

        val ex = shouldThrow<TerminalFailure> {
            fastPolicy.execute("test") {
                callCount.incrementAndGet()
                throw RetryableFailure("always fails")
            }
        }

        callCount.get() shouldBe 3
        ex.message shouldContain "Max retry attempts (3)"
    }

    test("throws TerminalFailure after max attempts exhausted on generic exception") {
        val callCount = AtomicInteger(0)

        shouldThrow<TerminalFailure> {
            fastPolicy.execute("test") {
                callCount.incrementAndGet()
                throw RuntimeException("hardware gone")
            }
        }

        callCount.get() shouldBe 3
    }

    test("does not retry TerminalFailure — fails fast") {
        val callCount = AtomicInteger(0)

        val ex = shouldThrow<TerminalFailure> {
            fastPolicy.execute("test") {
                callCount.incrementAndGet()
                throw TerminalFailure("bad config")
            }
        }

        callCount.get() shouldBe 1
        ex.message shouldContain "bad config"
    }

    test("succeeds after multiple transient failures within retry budget") {
        val successOnAttempt = 3
        val callCount = AtomicInteger(0)

        val result = fastPolicy.execute("test") {
            if (callCount.incrementAndGet() < successOnAttempt) {
                throw RetryableFailure("not yet")
            }
            "success on attempt $successOnAttempt"
        }

        result shouldBe "success on attempt 3"
        callCount.get() shouldBe successOnAttempt
    }

    test("respects maxAttempts configuration") {
        val callCount = AtomicInteger(0)
        val customPolicy = RecoveryPolicy(maxAttempts = 5, initialDelayMs = 1L, jitterMs = 0L)

        shouldThrow<TerminalFailure> {
            customPolicy.execute("test") {
                callCount.incrementAndGet()
                throw RuntimeException("always fails")
            }
        }

        callCount.get() shouldBe 5
    }

    test("TerminalFailure message contains operation name") {
        val ex = shouldThrow<TerminalFailure> {
            fastPolicy.execute("myDisplayOp") {
                throw RetryableFailure("error")
            }
        }

        ex.message shouldContain "myDisplayOp"
    }
})


