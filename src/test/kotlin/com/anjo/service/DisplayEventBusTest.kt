package com.anjo.service

import com.anjo.model.DisplayEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class DisplayEventBusTest : FunSpec({

    fun makeEvent(n: Int) = DisplayEvent(
        id = "id-$n",
        text = "text-$n",
        effect = "SCROLL",
        zoneId = null,
        displayedAt = "2026-06-22T00:00:0${n}Z"
    )

    test("emit then fresh collector receives the emitted DisplayEvent") {
        runTest {
            val bus = DisplayEventBus()
            val event = makeEvent(1)
            bus.emit(event)
            val received = bus.events.take(1).toList()
            received.size shouldBe 1
            received[0] shouldBe event
        }
    }

    test("after 6 emits fresh collector replay cache contains exactly the last 5 events in order") {
        runTest {
            val bus = DisplayEventBus()
            repeat(6) { bus.emit(makeEvent(it)) }
            val replayed = bus.events.take(5).toList()
            replayed.size shouldBe 5
            replayed[0] shouldBe makeEvent(1)
            replayed[1] shouldBe makeEvent(2)
            replayed[2] shouldBe makeEvent(3)
            replayed[3] shouldBe makeEvent(4)
            replayed[4] shouldBe makeEvent(5)
        }
    }

    test("bus events is SharedFlow of DisplayEvent and same instance across reads") {
        runTest {
            val bus = DisplayEventBus()
            val ref1: SharedFlow<DisplayEvent> = bus.events
            val ref2: SharedFlow<DisplayEvent> = bus.events
            ref1 shouldBeSameInstanceAs ref2
        }
    }
})
