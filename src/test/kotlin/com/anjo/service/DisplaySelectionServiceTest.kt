package com.anjo.service

import com.anjo.config.model.DisplayConfig
import com.anjo.driver.DisplayDriver
import com.anjo.model.DisplayType
import com.pi4j.context.Context
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify

class DisplaySelectionServiceTest : FunSpec({

    val context = mockk<Context>(relaxed = true)
    val config = DisplayConfig(type = DisplayType.MAX7219)

    test("should load startup driver from display config") {
        val startupDriver = mockk<DisplayDriver>(relaxed = true)
        val service = DisplaySelectionService(
            ctx = context, displayConfig = config,
            driverFactory = { type, _, _ -> if (type == "MAX7219") startupDriver else null }
        )
        service.currentDriver() shouldBe startupDriver
        service.getCurrentDisplayType() shouldBe "MAX7219"
    }

    test("should stop old driver and update type when switching display") {
        val maxDriver = mockk<DisplayDriver>(relaxed = true)
        val lcdDriver = mockk<DisplayDriver>(relaxed = true)
        val service = DisplaySelectionService(
            ctx = context, displayConfig = config,
            driverFactory = { type, _, _ -> when (type) { "MAX7219" -> maxDriver; "LCD" -> lcdDriver; else -> null } }
        )
        service.selectDisplay("lcd") shouldBe true
        service.currentDriver() shouldBe lcdDriver
        service.getCurrentDisplayType() shouldBe "LCD"
        verify(exactly = 1) { maxDriver.stop() }
    }

    test("should keep current driver unchanged on failed switch") {
        val maxDriver = mockk<DisplayDriver>(relaxed = true)
        val service = DisplaySelectionService(
            ctx = context, displayConfig = config,
            driverFactory = { type, _, _ -> if (type == "MAX7219") maxDriver else null }
        )
        service.selectDisplay("unsupported").shouldBeFalse()
        service.currentDriver() shouldBe maxDriver
    }

    test("should report UNKNOWN type when startup driver fails") {
        val service = DisplaySelectionService(
            ctx = context, displayConfig = DisplayConfig(type = DisplayType.OLED),
            driverFactory = { _, _, _ -> null }
        )
        service.currentDriver().shouldBeNull()
        service.getCurrentDisplayType() shouldBe "UNKNOWN"
    }
})
