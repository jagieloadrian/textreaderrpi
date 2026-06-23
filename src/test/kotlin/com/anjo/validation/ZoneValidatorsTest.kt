package com.anjo.validation

import com.anjo.model.AddZoneRequest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.server.plugins.requestvalidation.ValidationResult

class ZoneValidatorsTest : FunSpec({

    test("validateAddZone with blank name returns Invalid") {
        val result = ZoneValidators.validateAddZone(AddZoneRequest(name = "", type = "NETWORK", ip = "192.168.1.50"))
        (result as ValidationResult.Invalid).reasons.first() shouldBe "name cannot be blank"
    }

    test("validateAddZone with whitespace-only name returns Invalid") {
        val result = ZoneValidators.validateAddZone(AddZoneRequest(name = "  ", type = "NETWORK", ip = "192.168.1.50"))
        (result as ValidationResult.Invalid).reasons.first() shouldBe "name cannot be blank"
    }

    test("validateAddZone with name containing special chars returns Invalid") {
        val result = ZoneValidators.validateAddZone(AddZoneRequest(name = "zone<script>", type = "NETWORK", ip = "192.168.1.50"))
        (result as ValidationResult.Invalid).reasons.first() shouldContain "alphanumeric"
    }

    test("validateAddZone with name longer than 64 chars returns Invalid") {
        val longName = "a".repeat(65)
        val result = ZoneValidators.validateAddZone(AddZoneRequest(name = longName, type = "NETWORK", ip = "192.168.1.50"))
        (result as ValidationResult.Invalid).reasons.first() shouldContain "alphanumeric"
    }

    test("validateAddZone with name containing dots, hyphens, underscores returns Valid") {
        val result = ZoneValidators.validateAddZone(AddZoneRequest(name = "pico-salon_1.a", type = "FIRMWARE"))
        (result is ValidationResult.Valid) shouldBe true
    }

    test("validateAddZone with MAX 64 char name returns Valid") {
        val maxName = "a".repeat(64)
        val result = ZoneValidators.validateAddZone(AddZoneRequest(name = maxName, type = "FIRMWARE"))
        (result is ValidationResult.Valid) shouldBe true
    }

    test("validateAddZone with type MAX7219 returns Invalid with startup message") {
        val result = ZoneValidators.validateAddZone(AddZoneRequest(name = "zone1", type = "MAX7219"))
        (result as ValidationResult.Invalid).reasons.first() shouldContain "startup"
    }

    test("validateAddZone with type LCD returns Invalid with startup message") {
        val result = ZoneValidators.validateAddZone(AddZoneRequest(name = "zone1", type = "LCD"))
        (result as ValidationResult.Invalid).reasons.first() shouldContain "startup"
    }

    test("validateAddZone with type OLED returns Invalid with startup message") {
        val result = ZoneValidators.validateAddZone(AddZoneRequest(name = "zone1", type = "OLED"))
        (result as ValidationResult.Invalid).reasons.first() shouldContain "startup"
    }

    test("validateAddZone with type UNKNOWN returns Invalid with startup message") {
        val result = ZoneValidators.validateAddZone(AddZoneRequest(name = "zone1", type = "UNKNOWN"))
        (result as ValidationResult.Invalid).reasons.first() shouldContain "startup"
    }

    test("validateAddZone with type NETWORK and null ip returns Invalid") {
        val result = ZoneValidators.validateAddZone(AddZoneRequest(name = "zone1", type = "NETWORK", ip = null))
        (result as ValidationResult.Invalid).reasons.first() shouldBe "ip is required for network zones"
    }

    test("validateAddZone with type NETWORK and blank ip returns Invalid") {
        val result = ZoneValidators.validateAddZone(AddZoneRequest(name = "zone1", type = "NETWORK", ip = ""))
        (result as ValidationResult.Invalid).reasons.first() shouldBe "ip is required for network zones"
    }

    test("validateAddZone with type NETWORK and public ip returns Invalid") {
        val result = ZoneValidators.validateAddZone(AddZoneRequest(name = "zone1", type = "NETWORK", ip = "8.8.8.8"))
        (result as ValidationResult.Invalid).reasons.first() shouldContain "RFC1918"
    }

    test("validateAddZone with type NETWORK and private ip 192.168.x returns Valid") {
        val result = ZoneValidators.validateAddZone(AddZoneRequest(name = "zone1", type = "NETWORK", ip = "192.168.1.50"))
        (result is ValidationResult.Valid) shouldBe true
    }

    test("validateAddZone with type NETWORK and private ip 10.x returns Valid") {
        val result = ZoneValidators.validateAddZone(AddZoneRequest(name = "zone1", type = "NETWORK", ip = "10.0.0.1"))
        (result is ValidationResult.Valid) shouldBe true
    }

    test("validateAddZone with type NETWORK and private ip 172.16.x returns Valid") {
        val result = ZoneValidators.validateAddZone(AddZoneRequest(name = "zone1", type = "NETWORK", ip = "172.16.0.1"))
        (result is ValidationResult.Valid) shouldBe true
    }

    test("validateAddZone with type FIRMWARE and null ip returns Valid") {
        val result = ZoneValidators.validateAddZone(AddZoneRequest(name = "pico-salon", type = "FIRMWARE", ip = null))
        (result is ValidationResult.Valid) shouldBe true
    }

    test("validateAddZone with type FIRMWARE and displaySubtype returns Valid") {
        val result = ZoneValidators.validateAddZone(AddZoneRequest(name = "pico-salon", type = "FIRMWARE", displaySubtype = "MAX7219"))
        (result is ValidationResult.Valid) shouldBe true
    }
})
