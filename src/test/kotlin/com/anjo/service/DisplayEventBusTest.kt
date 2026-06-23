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

    test("tryEmit returns true and event is visible to fresh collector via replay") {
        runTest {
            val bus = DisplayEventBus()
            val event = makeEvent(1)
            val result = bus.tryEmit(event)
            result shouldBe true
            val received = bus.events.take(1).toList()
            received[0] shouldBe event
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
