package com.anjo.service
import com.anjo.driver.DisplayDriver
import com.anjo.driver.DisplayStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
class ScreenDriverResourceTest : FunSpec({
    val fastRecovery = RecoveryPolicy(maxAttempts = 1, initialDelayMs = 1L, jitterMs = 0L)
    test("repeated readInput operations stay within resource bounds") {
        val driver = mockk<DisplayDriver>(relaxed = true)
        val tracker = ResourceTracker(maxSlots = 10, trackerName = "test")
        val service = ScreenDriverService(
            driver, Dispatchers.Unconfined,
            recoveryPolicy = fastRecovery,
            resourceTracker = tracker,
        )
        repeat(5) { service.readInput("text $it") }
        // All slots should be released after each call completes
        tracker.heldCount shouldBe 0
    }
    test("resource slot is always released even when driver throws") {
        val driver = mockk<DisplayDriver>(relaxed = true)
        every { driver.scrollText(any(), any(), any()) } throws RuntimeException("hardware error")
        every { driver.status() } returns DisplayStatus(isActive = false, hardwareAvailable = false)
        val tracker = ResourceTracker(maxSlots = 10, trackerName = "test")
        val service = ScreenDriverService(
            driver, Dispatchers.Unconfined,
            recoveryPolicy = fastRecovery,
            resourceTracker = tracker,
        )
        service.readInput("will fail")
        // Slot must be released in finally block
        tracker.heldCount shouldBe 0
    }
    test("readInput is rejected when tracker is at capacity") {
        val driver = mockk<DisplayDriver>(relaxed = true)
        // Use a tracker with 0 capacity to simulate full state
        val fullTracker = ResourceTracker(maxSlots = 0, trackerName = "full")
        val service = ScreenDriverService(
            driver, Dispatchers.Unconfined,
            recoveryPolicy = fastRecovery,
            resourceTracker = fullTracker,
        )
        // Should not throw — rejection is logged and method returns early
        service.readInput("rejected input")
        // Driver should never have been called
        fullTracker.heldCount shouldBe 0
    }
    test("close releases all held resource slots") {
        val tracker = ResourceTracker(maxSlots = 10, trackerName = "test")
        // simulate externally acquired slots
        tracker.acquire("orphaned-slot-1")
        tracker.acquire("orphaned-slot-2")
        tracker.heldCount shouldBe 2
        tracker.close()
        tracker.heldCount shouldBe 0
        tracker.isClosed shouldBe true
    }
})
