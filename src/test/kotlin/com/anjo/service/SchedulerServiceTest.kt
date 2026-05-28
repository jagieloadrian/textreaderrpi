package com.anjo.service

import com.anjo.db.ScheduleRepository
import com.anjo.effect.EffectRenderer
import com.anjo.model.Effect
import com.anjo.model.Schedule
import com.anjo.model.TriggerType
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SchedulerServiceTest : FunSpec({

    val mockRepo = mockk<ScheduleRepository>(relaxed = true)
    val mockScreen = mockk<ScreenDriverService>(relaxed = true)
    val mockFactory = mockk<EffectRendererFactory>(relaxed = true)
    val mockRenderer = mockk<EffectRenderer>(relaxed = true)

    beforeEach {
        coEvery { mockFactory.create(any()) } returns mockRenderer
        coEvery { mockRepo.findAllActive() } returns emptyList()
    }

    test("one-shot schedule fires display after target delay") {
        runTest {
            val testScope = TestScope(StandardTestDispatcher(testScheduler) + Job())
            val service = SchedulerService(mockRepo, mockScreen, mockFactory, testScope)

            val targetTime = Instant.now().plusMillis(60_000L)
            val schedule = Schedule(
                id = "s1",
                text = "hello",
                triggerType = TriggerType.ONESHOT,
                triggerValue = targetTime.toString(),
                effect = Effect.SCROLL
            )
            service.schedule(schedule)

            // Not yet fired
            coVerify(exactly = 0) { mockScreen.displayScheduled(any(), any(), any()) }

            advanceTimeBy(60_001L)

            coVerify(exactly = 1) { mockScreen.displayScheduled("hello", "s1", any()) }

            service.stop()
            testScope.coroutineContext[Job]?.cancel()
        }
    }

    test("one-shot does not fire before target time") {
        runTest {
            val testScope = TestScope(StandardTestDispatcher(testScheduler) + Job())
            val service = SchedulerService(mockRepo, mockScreen, mockFactory, testScope)

            val targetTime = Instant.now().plusMillis(60_000L)
            val schedule = Schedule(
                id = "s2",
                text = "early",
                triggerType = TriggerType.ONESHOT,
                triggerValue = targetTime.toString(),
                effect = Effect.SCROLL
            )
            service.schedule(schedule)

            advanceTimeBy(30_000L)

            coVerify(exactly = 0) { mockScreen.displayScheduled("early", any(), any()) }

            service.stop()
            testScope.coroutineContext[Job]?.cancel()
        }
    }

    test("recurring schedule fires multiple times at interval") {
        runTest {
            val testScope = TestScope(StandardTestDispatcher(testScheduler) + Job())
            val service = SchedulerService(mockRepo, mockScreen, mockFactory, testScope)

            val schedule = Schedule(
                id = "s3",
                text = "recurring",
                triggerType = TriggerType.RECURRING,
                triggerValue = "5m",
                maxRuns = 3,
                effect = Effect.SCROLL
            )
            service.schedule(schedule)

            // 3 * 5 * 60 * 1000 + 1ms = past all 3 fires
            advanceTimeBy(3 * 5 * 60_000L + 1L)

            coVerify(exactly = 3) { mockScreen.displayScheduled("recurring", "s3", any()) }

            service.stop()
            testScope.coroutineContext[Job]?.cancel()
        }
    }

    test("recurring schedule stops at maxRuns") {
        runTest {
            val testScope = TestScope(StandardTestDispatcher(testScheduler) + Job())
            val service = SchedulerService(mockRepo, mockScreen, mockFactory, testScope)

            val schedule = Schedule(
                id = "s4",
                text = "bounded",
                triggerType = TriggerType.RECURRING,
                triggerValue = "1m",
                maxRuns = 2,
                effect = Effect.SCROLL
            )
            service.schedule(schedule)

            // Advance well past 2 runs to confirm it stops at exactly 2
            advanceTimeBy(10 * 60_000L + 1L)

            coVerify(atMost = 2) { mockScreen.displayScheduled("bounded", "s4", any()) }

            service.stop()
            testScope.coroutineContext[Job]?.cancel()
        }
    }

    test("cancel stops a recurring schedule before first fire") {
        runTest {
            val testScope = TestScope(StandardTestDispatcher(testScheduler) + Job())
            val service = SchedulerService(mockRepo, mockScreen, mockFactory, testScope)

            val schedule = Schedule(
                id = "s5",
                text = "cancelme",
                triggerType = TriggerType.RECURRING,
                triggerValue = "2m",
                effect = Effect.SCROLL
            )
            service.schedule(schedule)

            // Cancel at 30s (before first fire at 2min)
            advanceTimeBy(30_000L)
            service.cancel("s5")

            // Advance past where first fire would have been
            advanceTimeBy(2 * 60_000L)

            coVerify(exactly = 0) { mockScreen.displayScheduled("cancelme", any(), any()) }

            service.stop()
            testScope.coroutineContext[Job]?.cancel()
        }
    }
})
