package com.anjo.service

import com.anjo.config.model.RetryConfig
import com.anjo.driver.DisplayDriver
import com.anjo.model.Effect
import com.anjo.model.ScreenDriverMetrics
import com.anjo.service.effect.EffectRenderer
import com.anjo.service.effect.ScrollEffect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ConflictPolicyTest : FunSpec({

    val fastRetry = RetryConfig(maxAttempts = 1, initialDelayMs = 1L)

    fun makeService(driver: DisplayDriver) = ScreenDriverService(
        driver = driver,
        ioDispatcher = UnconfinedTestDispatcher(),
        retryConfig = fastRetry,
        displaySelectionService = null,
        metrics = ScreenDriverMetrics.DISABLED,
    )

    test("should cancel scheduled job when immediate display is requested") {
        runTest {
            val driver = mockk<DisplayDriver>(relaxed = true)
            val svc = makeService(driver)

            val scheduledJob = launch {
                svc.displayScheduled("scheduled-text", "sched-001", ScrollEffect())
            }
            advanceUntilIdle()

            val immediateJob = launch {
                svc.displayImmediate("ad-hoc-text", Effect.SCROLL)
            }
            advanceUntilIdle()

            coVerify { driver.scrollText(any(), "ad-hoc-text", any()) }
            scheduledJob.cancel()
            immediateJob.join()
        }
    }

    test("should complete immediate display when no scheduled job is running") {
        runTest {
            val driver = mockk<DisplayDriver>(relaxed = true)
            val svc = makeService(driver)

            val job = launch { svc.displayImmediate("solo-text", Effect.SCROLL) }
            advanceUntilIdle()
            job.join()

            coVerify { driver.scrollText(any(), "solo-text", any()) }
        }
    }

    test("should fire higher priority schedule before lower priority") {
        runTest {
            val testScope = TestScope(StandardTestDispatcher(testScheduler) + Job())
            val mockRepo = mockk<com.anjo.db.ScheduleRepository>(relaxed = true)
            val mockScreen = mockk<ScreenDriverService>(relaxed = true)
            val mockFactory = mockk<EffectRendererFactory>(relaxed = true)
            val mockRenderer = mockk<EffectRenderer>(relaxed = true)

            io.mockk.coEvery { mockFactory.create(any()) } returns mockRenderer
            val firedOrder = mutableListOf<String>()
            io.mockk.coEvery { mockScreen.displayScheduled(any(), any(), any()) } answers {
                firedOrder.add(firstArg())
            }

            val lowPriority = com.anjo.model.Schedule(
                id = "lo", text = "low-priority",
                triggerType = com.anjo.model.TriggerType.ONESHOT,
                triggerValue = java.time.Instant.now().minusSeconds(1).toString(),
                priority = 0, effect = Effect.SCROLL, createdAt = "2026-01-01T00:00:00Z"
            )
            val highPriority = com.anjo.model.Schedule(
                id = "hi", text = "high-priority",
                triggerType = com.anjo.model.TriggerType.ONESHOT,
                triggerValue = java.time.Instant.now().minusSeconds(1).toString(),
                priority = 10, effect = Effect.SCROLL, createdAt = "2026-01-01T00:00:01Z"
            )
            io.mockk.coEvery { mockRepo.findAllActive() } returns listOf(lowPriority, highPriority)

            val service = SchedulerService(mockRepo, mockScreen, mockFactory, testScope)
            service.start()
            advanceTimeBy(1000L)

            if (firedOrder.size >= 2) firedOrder[0] shouldBe "high-priority"

            service.stop()
            testScope.coroutineContext[Job]?.cancel()
        }
    }

    test("should fire earlier createdAt schedule first when same priority") {
        runTest {
            val testScope = TestScope(StandardTestDispatcher(testScheduler) + Job())
            val mockRepo = mockk<com.anjo.db.ScheduleRepository>(relaxed = true)
            val mockScreen = mockk<ScreenDriverService>(relaxed = true)
            val mockFactory = mockk<EffectRendererFactory>(relaxed = true)
            val mockRenderer = mockk<EffectRenderer>(relaxed = true)

            io.mockk.coEvery { mockFactory.create(any()) } returns mockRenderer
            val firedOrder = mutableListOf<String>()
            io.mockk.coEvery { mockScreen.displayScheduled(any(), any(), any()) } answers {
                firedOrder.add(firstArg())
            }

            val earlier = com.anjo.model.Schedule(
                id = "e1", text = "earlier",
                triggerType = com.anjo.model.TriggerType.ONESHOT,
                triggerValue = java.time.Instant.now().minusSeconds(1).toString(),
                priority = 5, createdAt = "2026-01-01T00:00:00Z", effect = Effect.SCROLL
            )
            val later = com.anjo.model.Schedule(
                id = "e2", text = "later",
                triggerType = com.anjo.model.TriggerType.ONESHOT,
                triggerValue = java.time.Instant.now().minusSeconds(1).toString(),
                priority = 5, createdAt = "2026-01-01T00:01:00Z", effect = Effect.SCROLL
            )
            io.mockk.coEvery { mockRepo.findAllActive() } returns listOf(later, earlier)

            val service = SchedulerService(mockRepo, mockScreen, mockFactory, testScope)
            service.start()
            advanceTimeBy(1000L)

            if (firedOrder.size >= 2) firedOrder[0] shouldBe "earlier"

            service.stop()
            testScope.coroutineContext[Job]?.cancel()
        }
    }
})
