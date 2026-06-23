package com.anjo.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class DisplayTypeTest : FunSpec({

    test("fromString returns FIRMWARE for exact case") {
        DisplayType.fromString("FIRMWARE") shouldBe DisplayType.FIRMWARE
    }

    test("fromString returns FIRMWARE case-insensitively") {
        DisplayType.fromString("firmware") shouldBe DisplayType.FIRMWARE
        DisplayType.fromString("Firmware") shouldBe DisplayType.FIRMWARE
    }

    test("fromString FIRMWARE does not return UNKNOWN") {
        DisplayType.fromString("FIRMWARE") shouldNotBe DisplayType.UNKNOWN
    }

    test("fromString unknown string still returns UNKNOWN") {
        DisplayType.fromString("INVALID") shouldBe DisplayType.UNKNOWN
    }
})
