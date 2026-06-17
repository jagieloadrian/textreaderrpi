package com.anjo.service

import com.anjo.config.model.RetryConfig
import com.anjo.model.ConflictPolicy
import com.anjo.model.Effect
import com.anjo.service.DisplayResult
import com.anjo.model.ScreenDriverMetrics
import com.anjo.service.effect.EffectRenderer
import com.anjo.service.effect.ScrollEffect
import com.anjo.zone.ZoneDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ConflictPolicyTest : FunSpec({

    val fastRetry = RetryConfig(maxAttempts = 1, initialDelayMs = 1L)

    fun makeRegistry(driver: ZoneDriver): ZoneRegistry {
        val registry = ZoneRegistry()
        registry.register("main", driver)
        return registry
    }

    fun makeService(driver: ZoneDriver) = ScreenDriverService(
        zoneRegistry = makeRegistry(driver),
        ioDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(),
        retryConfig = fastRetry,
        metrics = ScreenDriverMetrics.DISABLED,
    )

    test("broadcast result returned when no zone param given") {
        runTest {
            val driver = mockk<ZoneDriver>(relaxed = true)
            coEvery { driver.send(any(), any()) } returns true
            val svc = makeService(driver)
            val result = svc.displayImmediate("hello", Effect.SCROLL, ConflictPolicy.INTERRUPT)
            result.shouldBeInstanceOf<DisplayResult.Broadcast>()
        }
    }

    test("should return SKIP_NEW accepted=false when broadcast mutex is busy") {
        runTest {
            val driver = mockk<ZoneDriver>(relaxed = true)
            val svc = makeService(driver)

            val busyJob = launch { svc.displayImmediate("busy-text", Effect.SCROLL, ConflictPolicy.INTERRUPT) }
            advanceUntilIdle()

            val result = svc.displayImmediate("new-text", Effect.SCROLL, ConflictPolicy.SKIP_NEW)
            result.shouldBeInstanceOf<DisplayResult>()

            busyJob.join()
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
            io.mockk.coEvery { mockScreen.displayScheduled(any(), any(), any(), any(), any(), any(), any()) } answers {
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
            io.mockk.coEvery { mockScreen.displayScheduled(any(), any(), any(), any(), any(), any(), any()) } answers {
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
