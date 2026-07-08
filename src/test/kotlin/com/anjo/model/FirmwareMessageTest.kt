package com.anjo.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.json.Json

class FirmwareMessageTest : FunSpec({

    test("null timing fields are omitted from JSON output") {
        val msg = FirmwareMessage(text = "hello", effect = "SCROLL", zoneId = "z1", ts = "2024-01-01T00:00:00Z")
        val json = Json.Default.encodeToString(FirmwareMessage.serializer(), msg)
        json shouldNotContain "speed"
        json shouldNotContain "blinkPeriod"
        json shouldNotContain "fadeSteps"
    }

    test("non-null timing fields are present in JSON output") {
        val msg = FirmwareMessage(
            text = "hello",
            effect = "BLINK",
            zoneId = "z1",
            ts = "2024-01-01T00:00:00Z",
            speed = 50,
            blinkPeriod = 500,
            fadeSteps = 8
        )
        val json = Json.Default.encodeToString(FirmwareMessage.serializer(), msg)
        json shouldContain "\"speed\":50"
        json shouldContain "\"blinkPeriod\":500"
        json shouldContain "\"fadeSteps\":8"
    }

    test("base fields are always present in JSON output") {
        val msg = FirmwareMessage(text = "test", effect = "REVERSE", zoneId = "zone-a", ts = "2024-01-01T00:00:00Z")
        val json = Json.Default.encodeToString(FirmwareMessage.serializer(), msg)
        json shouldContain "\"text\":"
        json shouldContain "\"effect\":"
        json shouldContain "\"zoneId\":"
        json shouldContain "\"ts\":"
    }
})
