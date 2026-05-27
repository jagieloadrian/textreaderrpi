package com.anjo.driver

import com.anjo.config.model.DisplayConfig
import com.anjo.service.DisplaySelectionService
import com.anjo.service.ScreenDriverService
import com.pi4j.context.Context
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers

class DriverIntegrationTest : FunSpec({

    test("switches between max7219, lcd and oled drivers") {
        val context = mockk<Context>(relaxed = true)

        val max = FakeDriver("max")
        val lcd = FakeDriver("lcd")
        val oled = FakeDriver("oled")

        val selection = DisplaySelectionService(
            ctx = context,
            displayConfig = DisplayConfig(type = "MAX7219"),
            driverFactory = { type, _, _ ->
                when (type) {
                    "MAX7219" -> max
                    "LCD" -> lcd
                    "OLED" -> oled
                    else -> null
                }
            }
        )

        val service = ScreenDriverService(max, Dispatchers.Unconfined, selection)

        selection.getCurrentDisplayType() shouldBe "MAX7219"
        service.queueDisplaySwitch("lcd") shouldBe true
        selection.getCurrentDisplayType() shouldBe "LCD"
        service.currentDisplayType() shouldBe "LCD"

        service.queueDisplaySwitch("oled") shouldBe true
        selection.getCurrentDisplayType() shouldBe "OLED"
        service.currentDisplayType() shouldBe "OLED"

        selection.getPendingSwitches() shouldContainExactly listOf("LCD", "OLED")
    }

    test("rejects unknown display type") {
        val context = mockk<Context>(relaxed = true)
        val max = FakeDriver("max")

        val selection = DisplaySelectionService(
            ctx = context,
            displayConfig = DisplayConfig(type = "MAX7219"),
            driverFactory = { type, _, _ -> if (type == "MAX7219") max else null }
        )

        val service = ScreenDriverService(max, Dispatchers.Unconfined, selection)
        service.queueDisplaySwitch("unknown") shouldBe false
        selection.getCurrentDisplayType() shouldBe "MAX7219"
    }
})

private class FakeDriver(
    private val name: String,
) : DisplayDriver {
    private var last: String? = null

    override fun scrollText(scope: kotlinx.coroutines.CoroutineScope, text: String, speedMs: Long) {
        last = text
    }

    override fun clear() {
        last = null
    }

    override fun write(text: String) {
        last = text
    }

    override fun status(): DisplayStatus = DisplayStatus(
        isActive = false,
        hardwareAvailable = true,
        currentMessage = last,
        error = null
    )

    override fun stop() = Unit
}

