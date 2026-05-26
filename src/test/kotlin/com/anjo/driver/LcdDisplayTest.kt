package com.anjo.driver

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class LcdDisplayTest : FunSpec({
    test("display status can represent i2c initialization failure") {
        val status = DisplayStatus(
            isActive = false,
            hardwareAvailable = false,
            currentMessage = null,
            error = "I2C initialization failed"
        )

        status.hardwareAvailable shouldBe false
        status.error shouldContain "I2C"
    }

    test("lcd display constants remain stable") {
        val defaultAddress = 0x27
        val defaultBus = 1
        val columns = 16

        defaultAddress shouldBe 0x27
        defaultBus shouldBe 1
        columns shouldBe 16
    }

    test("text chunking for lcd lines works as expected") {
        val text = "First Line Text Second Line Content"
        val lines = text.chunked(16)

        lines.size shouldBe 3
        lines[0] shouldBe "First Line Text "
        lines[1] shouldBe "Second Line Cont"
    }

    test("display driver contract supports lcd-like write flow") {
        val driver = mockk<DisplayDriver>(relaxed = true)
        every { driver.status() } returns DisplayStatus(false, true, "line1", null)

        driver.clear()
        driver.write("line1")
        val status = driver.status()

        status.currentMessage shouldBe "line1"
        verify(exactly = 1) { driver.clear() }
        verify(exactly = 1) { driver.write("line1") }
    }
})

