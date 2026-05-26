package com.anjo.driver

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class Max7219MatrixTest : FunSpec({
    test("display status carries expected values") {
        val status = DisplayStatus(
            isActive = false,
            hardwareAvailable = true,
            currentMessage = "Test",
            error = null
        )

        status.isActive shouldBe false
        status.hardwareAvailable shouldBe true
        status.currentMessage shouldBe "Test"
        status.error shouldBe null
    }

    test("display status supports equality") {
        val first = DisplayStatus(true, true, "Hello", null)
        val second = DisplayStatus(true, true, "Hello", null)

        first shouldBe second
    }

    test("display status toString contains key fields") {
        val status = DisplayStatus(true, false, "Hello", "SPI error")

        status.toString() shouldContain "isActive"
        status.toString() shouldContain "hardwareAvailable"
        status.toString() shouldContain "Hello"
    }

    test("display driver contract can be mocked and invoked") {
        val driver = mockk<DisplayDriver>(relaxed = true)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        every { driver.status() } returns DisplayStatus(false, true, "ok", null)

        driver.clear()
        driver.write("sample")
        driver.scrollText(scope, "sample", 10)
        val status = driver.status()
        driver.stop()

        status.currentMessage shouldBe "ok"
        verify(exactly = 1) { driver.clear() }
        verify(exactly = 1) { driver.write("sample") }
        verify(exactly = 1) { driver.scrollText(scope, "sample", 10) }
        verify(exactly = 1) { driver.stop() }
    }
})

