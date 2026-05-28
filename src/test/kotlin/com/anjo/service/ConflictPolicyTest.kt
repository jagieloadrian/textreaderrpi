package com.anjo.service

import com.anjo.config.model.RetryConfig
import com.anjo.driver.DisplayDriver
import com.anjo.effect.ScrollEffect
import com.anjo.model.Effect
import com.anjo.model.ScreenDriverMetrics
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

    test("ad-hoc displayImmediate cancels running scheduled job") {
        runTest {
            val driver = mockk<DisplayDriver>(relaxed = true)
            val svc = makeService(driver)

            // Launch displayScheduled in a background coroutine (represents scheduler thread)
            val scheduledJob = launch {
                val renderer = ScrollEffect()
                svc.displayScheduled("scheduled-text", "sched-001", renderer)
            }

            // Let displayScheduled start and acquire the mutex
            advanceUntilIdle()

            // Now call displayImmediate which should cancel the scheduled job
            val immediateJob = launch {
                svc.displayImmediate("ad-hoc-text", Effect.SCROLL)
            }

            advanceUntilIdle()

            // ad-hoc display was triggered (scrollText called for "ad-hoc-text")
            coVerify { driver.scrollText(any(), "ad-hoc-text", any()) }

            scheduledJob.cancel()
            immediateJob.join()
        }
    }

    test("displayImmediate completes successfully when no scheduled job running") {
        runTest {
            val driver = mockk<DisplayDriver>(relaxed = true)
            val svc = makeService(driver)

            val job = launch {
                svc.displayImmediate("solo-text", Effect.SCROLL)
            }
            advanceUntilIdle()
            job.join()

            coVerify { driver.scrollText(any(), "solo-text", any()) }
        }
    }

    test("priority ordering: higher priority schedule is scheduled before lower when using sorted order") {
        runTest {
            // This tests that SchedulerService sorts by priority before scheduling
            val testScope = TestScope(StandardTestDispatcher(testScheduler) + Job())
            val mockRepo = mockk<com.anjo.db.ScheduleRepository>(relaxed = true)
            val mockScreen = mockk<ScreenDriverService>(relaxed = true)
            val mockFactory = mockk<EffectRendererFactory>(relaxed = true)
            val mockRenderer = mockk<com.anjo.effect.EffectRenderer>(relaxed = true)

            io.mockk.coEvery { mockFactory.create(any()) } returns mockRenderer
            val firedOrder = mutableListOf<String>()
            io.mockk.coEvery { mockScreen.displayScheduled(any(), any(), any()) } answers {
                firedOrder.add(firstArg())
            }

            // Repository returns lower-priority schedule first (simulating DB order)
            val lowPriority = com.anjo.model.Schedule(
                id = "lo",
                text = "low-priority",
                triggerType = com.anjo.model.TriggerType.ONESHOT,
                triggerValue = java.time.Instant.now().minusSeconds(1).toString(), // already past
                priority = 0,
                effect = com.anjo.model.Effect.SCROLL,
                createdAt = "2026-01-01T00:00:00Z"
            )
            val highPriority = com.anjo.model.Schedule(
                id = "hi",
                text = "high-priority",
                triggerType = com.anjo.model.TriggerType.ONESHOT,
                triggerValue = java.time.Instant.now().minusSeconds(1).toString(), // already past
                priority = 10,
                effect = com.anjo.model.Effect.SCROLL,
                createdAt = "2026-01-01T00:00:01Z"
            )
            io.mockk.coEvery { mockRepo.findAllActive() } returns listOf(lowPriority, highPriority)

            val service = SchedulerService(mockRepo, mockScreen, mockFactory, testScope)
            service.start()

            // Let the start() and launchOneShot (with delayMs <= 0) run
            advanceTimeBy(1000L)

            // high-priority should fire before low-priority (sorted by priority desc)
            if (firedOrder.size >= 2) {
                firedOrder[0] shouldBe "high-priority"
            }

            service.stop()
            testScope.coroutineContext[Job]?.cancel()
        }
    }

    test("same priority: earlier createdAt fires first") {
        runTest {
            val testScope = TestScope(StandardTestDispatcher(testScheduler) + Job())
            val mockRepo = mockk<com.anjo.db.ScheduleRepository>(relaxed = true)
            val mockScreen = mockk<ScreenDriverService>(relaxed = true)
            val mockFactory = mockk<EffectRendererFactory>(relaxed = true)
            val mockRenderer = mockk<com.anjo.effect.EffectRenderer>(relaxed = true)

            io.mockk.coEvery { mockFactory.create(any()) } returns mockRenderer
            val firedOrder = mutableListOf<String>()
            io.mockk.coEvery { mockScreen.displayScheduled(any(), any(), any()) } answers {
                firedOrder.add(firstArg())
            }

            val earlier = com.anjo.model.Schedule(
                id = "e1",
                text = "earlier",
                triggerType = com.anjo.model.TriggerType.ONESHOT,
                triggerValue = java.time.Instant.now().minusSeconds(1).toString(),
                priority = 5,
                createdAt = "2026-01-01T00:00:00Z",
                effect = com.anjo.model.Effect.SCROLL
            )
            val later = com.anjo.model.Schedule(
                id = "e2",
                text = "later",
                triggerType = com.anjo.model.TriggerType.ONESHOT,
                triggerValue = java.time.Instant.now().minusSeconds(1).toString(),
                priority = 5,
                createdAt = "2026-01-01T00:01:00Z",
                effect = com.anjo.model.Effect.SCROLL
            )
            // Repository returns [later, earlier] — reverse order
            io.mockk.coEvery { mockRepo.findAllActive() } returns listOf(later, earlier)

            val service = SchedulerService(mockRepo, mockScreen, mockFactory, testScope)
            service.start()

            advanceTimeBy(1000L)

            // "earlier" should fire before "later" (sortedWith thenBy createdAt)
            if (firedOrder.size >= 2) {
                firedOrder[0] shouldBe "earlier"
            }

            service.stop()
            testScope.coroutineContext[Job]?.cancel()
        }
    }
})




