package com.anjo.zone

import com.anjo.model.ZoneStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import kotlinx.serialization.json.Json

class ZoneStatusTest : FunSpec({
    test("ZoneStatus should serialize to JSON with required fields") {
        val status = ZoneStatus(id = "main", type = "MAX7219", status = "ONLINE")
        val json = Json.encodeToString(ZoneStatus.serializer(), status)
        json shouldBe """{"id":"main","type":"MAX7219","status":"ONLINE"}"""
    }

    test("ZoneStatus should include optional ip and lastSeenAt when present") {
        val status = ZoneStatus(
            id = "kitchen",
            type = "MAX7219",
            status = "ONLINE",
            ip = "192.168.1.50",
            lastSeenAt = "2026-06-16T10:00:00Z"
        )
        status.ip shouldBe "192.168.1.50"
        status.lastSeenAt shouldBe "2026-06-16T10:00:00Z"
    }

    test("ZoneStatus ip and lastSeenAt default to null") {
        val status = ZoneStatus(id = "main", type = "OLED", status = "OFFLINE")
        status.ip.shouldBeNull()
        status.lastSeenAt.shouldBeNull()
    }
})
