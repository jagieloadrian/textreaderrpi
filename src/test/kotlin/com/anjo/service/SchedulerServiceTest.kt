package com.anjo.service

import com.anjo.db.ScheduleRepository
import com.anjo.service.effect.EffectRenderer
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

    test("should fire display after target delay for ONESHOT schedule") {
        runTest {
            val testScope = TestScope(StandardTestDispatcher(testScheduler) + Job())
            val service = SchedulerService(mockRepo, mockScreen, mockFactory, testScope)

            val schedule = Schedule(
                id = "s1", text = "hello",
                triggerType = TriggerType.ONESHOT,
                triggerValue = Instant.now().plusMillis(60_000L).toString(),
                effect = Effect.SCROLL
            )
            service.schedule(schedule)

            coVerify(exactly = 0) { mockScreen.displayScheduled(any(), any(), any()) }
            advanceTimeBy(60_001L)
            coVerify(exactly = 1) { mockScreen.displayScheduled("hello", "s1", any()) }

            service.stop()
            testScope.coroutineContext[Job]?.cancel()
        }
    }

    test("should not fire ONESHOT schedule before target time") {
        runTest {
            val testScope = TestScope(StandardTestDispatcher(testScheduler) + Job())
            val service = SchedulerService(mockRepo, mockScreen, mockFactory, testScope)

            val schedule = Schedule(
                id = "s2", text = "early",
                triggerType = TriggerType.ONESHOT,
                triggerValue = Instant.now().plusMillis(60_000L).toString(),
                effect = Effect.SCROLL
            )
            service.schedule(schedule)
            advanceTimeBy(30_000L)
            coVerify(exactly = 0) { mockScreen.displayScheduled("early", any(), any()) }

            service.stop()
            testScope.coroutineContext[Job]?.cancel()
        }
    }

    test("should fire RECURRING schedule at each interval") {
        runTest {
            val testScope = TestScope(StandardTestDispatcher(testScheduler) + Job())
            val service = SchedulerService(mockRepo, mockScreen, mockFactory, testScope)

            val schedule = Schedule(
                id = "s3", text = "recurring",
                triggerType = TriggerType.RECURRING, triggerValue = "5m",
                maxRuns = 3, effect = Effect.SCROLL
            )
            service.schedule(schedule)
            advanceTimeBy(3 * 5 * 60_000L + 1L)
            coVerify(exactly = 3) { mockScreen.displayScheduled("recurring", "s3", any()) }

            service.stop()
            testScope.coroutineContext[Job]?.cancel()
        }
    }

    test("should stop RECURRING schedule after maxRuns") {
        runTest {
            val testScope = TestScope(StandardTestDispatcher(testScheduler) + Job())
            val service = SchedulerService(mockRepo, mockScreen, mockFactory, testScope)

            val schedule = Schedule(
                id = "s4", text = "bounded",
                triggerType = TriggerType.RECURRING, triggerValue = "1m",
                maxRuns = 2, effect = Effect.SCROLL
            )
            service.schedule(schedule)
            advanceTimeBy(10 * 60_000L + 1L)
            coVerify(atMost = 2) { mockScreen.displayScheduled("bounded", "s4", any()) }

            service.stop()
            testScope.coroutineContext[Job]?.cancel()
        }
    }

    test("should not write DONE to repository when scheduling new job") {
        runTest {
            val testScope = TestScope(StandardTestDispatcher(testScheduler) + Job())
            val service = SchedulerService(mockRepo, mockScreen, mockFactory, testScope)

            val schedule = Schedule(
                id = "s-new", text = "new",
                triggerType = TriggerType.RECURRING, triggerValue = "10m",
                effect = Effect.SCROLL
            )
            service.schedule(schedule)
            coVerify(exactly = 0) { mockRepo.updateStatus("s-new", "DONE") }

            service.stop()
            testScope.coroutineContext[Job]?.cancel()
        }
    }

    test("should not mark DONE when rescheduling same id") {
        runTest {
            val testScope = TestScope(StandardTestDispatcher(testScheduler) + Job())
            val service = SchedulerService(mockRepo, mockScreen, mockFactory, testScope)

            val schedule = Schedule(
                id = "s-re", text = "reschedule",
                triggerType = TriggerType.RECURRING, triggerValue = "10m",
                effect = Effect.SCROLL
            )
            service.schedule(schedule)
            service.schedule(schedule)
            coVerify(exactly = 0) { mockRepo.updateStatus("s-re", "DONE") }

            service.stop()
            testScope.coroutineContext[Job]?.cancel()
        }
    }

    test("should write DONE to repository and stop coroutine on cancel") {
        runTest {
            val testScope = TestScope(StandardTestDispatcher(testScheduler) + Job())
            val service = SchedulerService(mockRepo, mockScreen, mockFactory, testScope)

            val schedule = Schedule(
                id = "s-cxl", text = "to cancel",
                triggerType = TriggerType.RECURRING, triggerValue = "10m",
                effect = Effect.SCROLL
            )
            service.schedule(schedule)
            service.cancel("s-cxl")
            advanceTimeBy(1L)

            coVerify(exactly = 1) { mockRepo.updateStatus("s-cxl", "DONE") }
            coVerify(exactly = 0) { mockScreen.displayScheduled(any(), "s-cxl", any()) }

            service.stop()
            testScope.coroutineContext[Job]?.cancel()
        }
    }

    test("should stop recurring schedule before first fire when cancelled") {
        runTest {
            val testScope = TestScope(StandardTestDispatcher(testScheduler) + Job())
            val service = SchedulerService(mockRepo, mockScreen, mockFactory, testScope)

            val schedule = Schedule(
                id = "s5", text = "cancelme",
                triggerType = TriggerType.RECURRING, triggerValue = "2m",
                effect = Effect.SCROLL
            )
            service.schedule(schedule)
            advanceTimeBy(30_000L)
            service.cancel("s5")
            advanceTimeBy(2 * 60_000L)

            coVerify(exactly = 0) { mockScreen.displayScheduled("cancelme", any(), any()) }

            service.stop()
            testScope.coroutineContext[Job]?.cancel()
        }
    }
})
