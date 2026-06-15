package com.anjo.service

import com.anjo.config.model.RetryConfig
import com.anjo.db.HistoryRepository
import com.anjo.model.ConflictPolicy
import com.anjo.model.Effect
import com.anjo.model.HistoryRecord
import com.anjo.service.effect.ScrollEffect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest

class HistoryRecordingTest : FunSpec({

    val fastRetry = RetryConfig(maxAttempts = 1, initialDelayMs = 1L)

    fun service(historyRepository: HistoryRepository?) = ScreenDriverService(
        driver = com.anjo.driver.OfflineDisplayDriver,
        ioDispatcher = Dispatchers.Unconfined,
        retryConfig = fastRetry,
        displaySelectionService = null,
        metrics = com.anjo.model.ScreenDriverMetrics.DISABLED,
        historyRepository = historyRepository,
    )

    test("displayImmediate INTERRUPT records IMMEDIATE history") {
        runTest {
            val repo = mockk<HistoryRepository>()
            coEvery { repo.insert(any()) } returns HistoryRecord(text = "hello", effect = "SCROLL", source = "IMMEDIATE")
            val svc = service(repo)
            svc.displayImmediate("hello", Effect.SCROLL, ConflictPolicy.INTERRUPT).shouldBeTrue()
            coVerify(exactly = 1) { repo.insert(match { it.source == "IMMEDIATE" && it.text == "hello" && it.effect == "SCROLL" }) }
        }
    }

    test("displayImmediate SKIP_NEW records IMMEDIATE history when not busy") {
        runTest {
            val repo = mockk<HistoryRepository>()
            coEvery { repo.insert(any()) } returns HistoryRecord(text = "skip-test", effect = "BLINK", source = "IMMEDIATE")
            val svc = service(repo)
            svc.displayImmediate("skip-test", Effect.BLINK, ConflictPolicy.SKIP_NEW).shouldBeTrue()
            coVerify(exactly = 1) { repo.insert(match { it.source == "IMMEDIATE" && it.text == "skip-test" && it.effect == "BLINK" }) }
        }
    }

    test("displayScheduled INTERRUPT records SCHEDULED history") {
        runTest {
            val repo = mockk<HistoryRepository>()
            coEvery { repo.insert(any()) } returns HistoryRecord(text = "sched", effect = "SCROLL", source = "SCHEDULED")
            val svc = service(repo)
            svc.displayScheduled("sched", "sched-id-1", ScrollEffect(), Effect.SCROLL, ConflictPolicy.INTERRUPT).shouldBeTrue()
            coVerify(exactly = 1) { repo.insert(match { it.source == "SCHEDULED" && it.scheduleId == "sched-id-1" && it.effect == "SCROLL" }) }
        }
    }

    test("displayScheduled SKIP_NEW records SCHEDULED history when not busy") {
        runTest {
            val repo = mockk<HistoryRepository>()
            coEvery { repo.insert(any()) } returns HistoryRecord(text = "sched2", effect = "SCROLL", source = "SCHEDULED")
            val svc = service(repo)
            svc.displayScheduled("sched2", "sched-id-2", ScrollEffect(), Effect.SCROLL, ConflictPolicy.SKIP_NEW).shouldBeTrue()
            coVerify(exactly = 1) { repo.insert(match { it.source == "SCHEDULED" && it.scheduleId == "sched-id-2" }) }
        }
    }

    test("displayImmediate returns true even when historyRepository.insert throws") {
        runTest {
            val repo = mockk<HistoryRepository>()
            coEvery { repo.insert(any()) } throws RuntimeException("DB down")
            val svc = service(repo)
            val result = svc.displayImmediate("fail-insert", Effect.SCROLL, ConflictPolicy.INTERRUPT)
            result.shouldBeTrue()
        }
    }

    test("historyRepository null does not cause displayImmediate to fail") {
        runTest {
            val svc = service(null)
            svc.displayImmediate("no-repo", Effect.SCROLL).shouldBeTrue()
        }
    }
})
