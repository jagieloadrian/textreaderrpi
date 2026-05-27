package com.anjo.service

import com.codahale.metrics.MetricRegistry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

class ResourceTrackerTest : FunSpec({
    test("acquire returns valid slot id") {
        val tracker = ResourceTracker(maxSlots = 5)
        val id = tracker.acquire("test-resource")
        id shouldBeGreaterThan 0L
        tracker.heldCount shouldBe 1
    }
    test("release decrements held count") {
        val tracker = ResourceTracker(maxSlots = 5)
        val id = tracker.acquire("test-resource")
        tracker.release(id)
        tracker.heldCount shouldBe 0
    }
    test("acquire returns -1 when at capacity") {
        val tracker = ResourceTracker(maxSlots = 2)
        val id1 = tracker.acquire("r1")
        val id2 = tracker.acquire("r2")
        val id3 = tracker.acquire("r3")
        id1 shouldBeGreaterThan 0L
        id2 shouldBeGreaterThan 0L
        id3 shouldBe -1L
        tracker.heldCount shouldBe 2
    }
    test("release is idempotent for unknown slot id") {
        val tracker = ResourceTracker(maxSlots = 5)
        // releasing unknown id should not throw
        tracker.release(9999L)
        tracker.heldCount shouldBe 0
    }
    test("acquire returns -1 when tracker is closed") {
        val tracker = ResourceTracker(maxSlots = 5)
        tracker.close()
        val id = tracker.acquire("post-close")
        id shouldBe -1L
    }
    test("close releases all held slots") {
        val tracker = ResourceTracker(maxSlots = 10)
        tracker.acquire("r1")
        tracker.acquire("r2")
        tracker.acquire("r3")
        tracker.heldCount shouldBe 3
        tracker.close()
        tracker.heldCount shouldBe 0
        tracker.isClosed shouldBe true
    }
    test("close is idempotent") {
        val tracker = ResourceTracker(maxSlots = 5)
        tracker.acquire("r1")
        tracker.close()
        tracker.close() // should not throw
        tracker.heldCount shouldBe 0
    }
    test("released slots free capacity for new acquires") {
        val tracker = ResourceTracker(maxSlots = 2)
        val id1 = tracker.acquire("r1")
        tracker.acquire("r2")
        tracker.release(id1)
        val id3 = tracker.acquire("r3")
        id3 shouldBeGreaterThan 0L
        tracker.heldCount shouldBe 2
    }

    // --- snapshot tests ---

    test("snapshot reflects initial state") {
        val tracker = ResourceTracker(maxSlots = 5)
        val snap = tracker.snapshot
        snap.capacity shouldBe 5
        snap.active shouldBe 0
        snap.available shouldBe 5
        snap.isClosed shouldBe false
    }

    test("snapshot updates after acquire") {
        val tracker = ResourceTracker(maxSlots = 5)
        tracker.acquire("r1")
        val snap = tracker.snapshot
        snap.active shouldBe 1
        snap.available shouldBe 4
        snap.isClosed shouldBe false
    }

    test("snapshot returns to initial after release") {
        val tracker = ResourceTracker(maxSlots = 5)
        val id = tracker.acquire("r1")
        tracker.release(id)
        val snap = tracker.snapshot
        snap.active shouldBe 0
        snap.available shouldBe 5
    }

    test("snapshot isClosed is true after close") {
        val tracker = ResourceTracker(maxSlots = 5)
        tracker.close()
        tracker.snapshot.isClosed shouldBe true
    }

    test("MetricRegistry gauges are registered when metricRegistry is provided") {
        val registry = MetricRegistry()
        ResourceTracker(maxSlots = 3, trackerName = "test", metricRegistry = registry)
        registry.gauges.containsKey("resource.tracker.test.active") shouldBe true
        registry.gauges.containsKey("resource.tracker.test.available") shouldBe true
    }
})
